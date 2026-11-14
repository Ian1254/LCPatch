// Included inside the core's anonymous namespace after ELF/image helpers.
// Bootstrap from existing font/localization callbacks on an attached game thread.
// Never patch the shared IL2CPP resolver or call domain_get before a real runtime callback.
// No runtime_invoke hook, worker-thread managed allocation, or fixed game RVA.
// Unity 6's TextAsset.text is managed decoding of get_bytes. Only patch the
// Unity bytes icall, and only substitute when its return address proves the
// caller is the managed text getter. Raw bytes and unknown contexts stay original.
struct TextAssetApi {
    void *(*domainGet)() = nullptr;
    const void **(*assemblies)(void *, size_t *) = nullptr;
    const void *(*assemblyImage)(const void *) = nullptr;
    const void *(*corlib)() = nullptr;
    void *(*classFromName)(const void *, const char *, const char *) = nullptr;
    void *(*classFromType)(const void *) = nullptr;
    void *(*elementClass)(void *) = nullptr;
    const void *(*method)(void *, const char *, int) = nullptr;
    uint32_t (*flags)(const void *, uint32_t *) = nullptr;
    uint32_t (*paramCount)(const void *) = nullptr;
    const void *(*param)(const void *, uint32_t) = nullptr;
    const void *(*returnType)(const void *) = nullptr;
    int (*typeKind)(const void *) = nullptr;
    bool (*isByref)(const void *) = nullptr;
    bool (*isValueType)(const void *) = nullptr;
    int32_t (*valueSize)(void *, uint32_t *) = nullptr;
    void *(*field)(void *, const char *) = nullptr;
    size_t (*fieldOffset)(const void *) = nullptr;
    const void *(*fieldType)(const void *) = nullptr;
    uint32_t (*objectHeaderSize)() = nullptr;
    uint32_t (*arrayHeaderSize)() = nullptr;
    void *(*arrayNew)(void *, uintptr_t) = nullptr;
    uint32_t (*arrayLength)(void *) = nullptr;
    uint32_t (*arrayBytes)(void *) = nullptr;
    void *(*currentThread)() = nullptr;
};
TextAssetApi gTextApi{};
UnitySearch gTextImage{};
UnitySearch gTextUnityImage{};
using ResolveIcall = void *(*)(const char *);
using TextBytesGetter = void *(*)(void *);
using IsMainThread = bool (*)();
struct NativeNameSpan { const uint16_t *begin; int32_t length; };
using NativeGetName = void (*)(void *, NativeNameSpan *);
using NativeFree = void (*)(void *);
std::atomic<ResolveIcall> gResolveIcall{nullptr};
IsMainThread gTextIsMainThread = nullptr;
std::atomic<TextBytesGetter> gTextOriginal{nullptr};
NativeGetName gNativeGetName = nullptr;
NativeFree gNativeNameFree = nullptr;
void *gTextByteClass = nullptr;
uintptr_t gTextReadReturn = 0;
uint32_t gTextArrayHeader = 0;
std::atomic<unsigned> gTextInstallState{0};
std::atomic<unsigned> gTextSwaps{0};
std::atomic<unsigned> gBootstrapObserved{0};
std::atomic<bool> gTextConfigured{false};
bool gTextPrepared = false;
std::atomic<unsigned> gTextMisses{0};
std::atomic<unsigned> gTextThreadSkips{0};
std::atomic<unsigned> gTextContextSkips{0};
unsigned gTextInstallAttempts = 0;
thread_local bool gInTextInstall = false;
thread_local bool gInTextRead = false;
std::unordered_map<std::string, std::string> gTextIndex;

template <typename Fn>
Fn publishedOriginal(const std::atomic<Fn> &slot) {
    Fn target = slot.load(std::memory_order_acquire);
    while (!target) { sched_yield(); target = slot.load(std::memory_order_acquire); }
    return target;
}

std::string readTextAssetFile(const std::string &path, size_t maximum, const char **failureReason = nullptr, int *failureErrno = nullptr) {
    if (failureReason) *failureReason = "ok";
    if (failureErrno) *failureErrno = 0;
    const auto failed = [&](const char *reason, int error) {
        if (failureReason) *failureReason = reason;
        if (failureErrno) *failureErrno = error;
    };
    const int fd = static_cast<int>(syscall(SYS_openat, AT_FDCWD, path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW));
    if (fd < 0) { failed("open_failed", errno); return {}; }
    struct stat info{};
    std::string text;
    if (fstat(fd, &info) != 0) failed("stat_failed", errno);
    else if (!S_ISREG(info.st_mode)) failed("not_regular_file", 0);
    else if (info.st_size <= 0) failed("empty_file", 0);
    else if (static_cast<uint64_t>(info.st_size) > maximum) failed("size_limit", 0);
    else {
        text.resize(static_cast<size_t>(info.st_size));
        size_t position = 0;
        while (position < text.size()) {
            const auto count = syscall(SYS_read, fd, &text[position], text.size() - position);
            if (count <= 0) { failed(count < 0 ? "read_failed" : "unexpected_eof", count < 0 ? errno : 0); text.clear(); break; }
            position += static_cast<size_t>(count);
        }
    }
    syscall(SYS_close, fd);
    return text;
}

void *hookedTextAssetBytes(void *nativeObject) {
    // Capture the engine caller before invoking any other function. The verified
    // managed bytes wrapper tail-calls this icall, retaining text getter's LR.
    const uintptr_t caller = reinterpret_cast<uintptr_t>(__builtin_return_address(0));
    const auto original = publishedOriginal(gTextOriginal);
    void *official = original(nativeObject);
    if (!official || !nativeObject || gInTextInstall || gInTextRead) return official;
    if (caller != gTextReadReturn) {
        if (gTextContextSkips.fetch_add(1, std::memory_order_relaxed) < 4)
            breadcrumb("INFO", "core.textasset.context_skip", "Non-text or unknown bytes caller retained; caller=+0x%lx",
                static_cast<unsigned long>(caller >= gTextImage.base ? caller-gTextImage.base : 0));
        return official;
    }
    if (!gTextApi.currentThread() || !gTextIsMainThread || !gTextIsMainThread()) {
        if (gTextThreadSkips.fetch_add(1, std::memory_order_relaxed) < 4)
            breadcrumb("INFO", "core.textasset.thread_skip", "Original bytes retained outside attached Unity main thread");
        return official;
    }
    if (!gVerificationProfile.load(std::memory_order_acquire)) return official;
    if (!gVerificationReady.load(std::memory_order_acquire) && verificationEntryReady()) {
        if (!gVerificationReady.exchange(true, std::memory_order_acq_rel))
            breadcrumb("INFO", "core.io.verify_guard.ready", "source=TextAsset; read-only stack guard");
    }
    // Do not stop at a module boundary: this route crosses libunity/IL2CPP.
    if (!gVerificationReady.load(std::memory_order_acquire) ||
        lcpatch::isResourceVerificationRead(gVerificationRange)) return official;
    gInTextRead = true;
    struct ReadGuard { ~ReadGuard() { gInTextRead = false; } } readGuard;
    NativeNameSpan span{};
    gNativeGetName(nativeObject, &span);
    // Unity's observed StringMarshaller frees only positive-length spans. The
    // length-zero non-null value is a sentinel, not allocated string storage.
    struct SpanGuard {
        NativeNameSpan &span;
        ~SpanGuard() { if (span.length > 0 && span.begin) gNativeNameFree(const_cast<uint16_t *>(span.begin)); }
    } spanGuard{span};
    if (!span.begin || span.length <= 0 || span.length > 1024) return official;
    std::string key;
    key.reserve(static_cast<size_t>(span.length));
    for (int32_t i = 0; i < span.length; ++i) {
        if (span.begin[i] < 32 || span.begin[i] > 126) return official;
        key.push_back(static_cast<char>(span.begin[i]));
    }
    key = lcpatch::assetKey(key);
    if (!lcpatch::textAssetDisplayResource(key)) return official;
    const auto selected = gTextIndex.find(key);
    if (selected == gTextIndex.end()) {
        if (gTextMisses.fetch_add(1, std::memory_order_relaxed) < 4)
            breadcrumb("INFO", "core.textasset.lookup_miss", "display_asset=%s", key.c_str());
        return official;
    }
    const auto text = readTextAssetFile(std::string(kRuntimeLocalize) + "/" + selected->second, 8U * 1024U * 1024U);
    const auto first = text.find_first_not_of(" \t\r\n");
    if (first == std::string::npos || (text[first] != '{' && text[first] != '[') || text.find('\0') != std::string::npos)
        return official;
    void *replacement = gTextApi.arrayNew(gTextByteClass, text.size());
    if (!replacement || gTextApi.arrayLength(replacement) != text.size() ||
        gTextApi.arrayBytes(replacement) != text.size()) return official;
    std::memcpy(static_cast<uint8_t *>(replacement)+gTextArrayHeader, text.data(), text.size());
    const unsigned count = gTextSwaps.fetch_add(1, std::memory_order_relaxed) + 1;
    if (count <= 16 || count % 128 == 0)
        breadcrumb("INFO", "core.textasset.redirect", "asset=%s file=%s count=%u route=unity_bytes_text_only", key.c_str(), selected->second.c_str(), count);
    return replacement;
}

const void *checkedTextMethod(void *klass, const char *name, int parameters, bool isStatic, int resultKind) {
    if (!klass) return nullptr;
    const void *method = gTextApi.method(klass, name, parameters);
    if (!method) return nullptr;
    uint32_t implementation = 0;
    const void *type = gTextApi.returnType(method);
    if (gTextApi.paramCount(method) != static_cast<uint32_t>(parameters) ||
        ((gTextApi.flags(method, &implementation) & 0x10U) != 0) != isStatic ||
        !type || gTextApi.typeKind(type) != resultKind) return nullptr;
    return method;
}

uintptr_t textMethodEntry(const void *method) {
    uintptr_t entry = 0;
    if (method) std::memcpy(&entry, method, sizeof(entry));
    return rangeContains(gTextImage.executable, gTextImage.count, entry, 128) &&
        rangeContains(gTextImage.readable, gTextImage.readableCount, entry, 128) ? entry : 0;
}

bool textReadInstruction(uintptr_t at, uint32_t &word) {
    if (!rangeContains(gTextImage.executable, gTextImage.count, at, 4) ||
        !rangeContains(gTextImage.readable, gTextImage.readableCount, at, 4)) return false;
    std::memcpy(&word, reinterpret_cast<const void *>(at), 4);
    return true;
}

// Discover text->bytes BL from the current metadata entries. No fixed RVA or
// guessed range: the supported wrapper must also have a verified tail BR path.
uintptr_t textGetterBytesReturn(uintptr_t text, uintptr_t bytes) {
    return lcpatch::findTextBytesReturn(text, bytes, textReadInstruction);
}

bool textParamKind(const void *method, unsigned index, int kind) {
    const void *type = method ? gTextApi.param(method, index) : nullptr;
    return type && gTextApi.typeKind(type) == kind && !gTextApi.isByref(type);
}

void tryInstallTextAssetHook() {
    if (gInTextInstall || !gTextApi.currentThread()) return;
    unsigned expected = 0;
    if (!gTextInstallState.compare_exchange_strong(expected, 1, std::memory_order_acq_rel)) return;
    gInTextInstall = true;
    struct InstallGuard { ~InstallGuard() { gInTextInstall = false; } } installGuard;
    const auto resolver = gResolveIcall.load(std::memory_order_acquire);
    const auto unityIcall = [&](const char *name) -> void * {
        void *entry = resolver ? resolver(name) : nullptr;
        return rangeContains(gTextUnityImage.executable, gTextUnityImage.count, reinterpret_cast<uintptr_t>(entry), 16) ? entry : nullptr;
    };
    void *mainCheck = unityIcall("UnityEngine.Object::CurrentThreadIsMainThread()");
    if (!mainCheck) { gTextInstallState.store(3, std::memory_order_release); return; }
    gTextIsMainThread = reinterpret_cast<IsMainThread>(mainCheck);
    if (!gTextIsMainThread()) { gTextInstallState.store(0, std::memory_order_release); return; }
    ++gTextInstallAttempts;
    void *domain = gTextApi.domainGet();
    size_t count = 0;
    const void **assemblies = domain ? gTextApi.assemblies(domain, &count) : nullptr;
    void *textClass = nullptr, *objectClass = nullptr, *spanClass = nullptr, *allocatorClass = nullptr;
    if (assemblies && count < 4096) for (size_t i = 0; i < count; ++i) {
        const void *image = gTextApi.assemblyImage(assemblies[i]);
        if (!image) continue;
        if (!textClass) textClass = gTextApi.classFromName(image, "UnityEngine", "TextAsset");
        if (!objectClass) objectClass = gTextApi.classFromName(image, "UnityEngine", "Object");
        if (!spanClass) spanClass = gTextApi.classFromName(image, "UnityEngine.Bindings", "ManagedSpanWrapper");
        if (!allocatorClass) allocatorClass = gTextApi.classFromName(image, "UnityEngine.Bindings", "BindingsAllocator");
    }
    const void *textMethod = checkedTextMethod(textClass, "get_text", 0, false, 0x0e);
    const void *bytesMethod = checkedTextMethod(textClass, "get_bytes", 0, false, 0x1d);
    const void *injectedBytes = checkedTextMethod(textClass, "get_bytes_Injected", 1, true, 0x1d);
    const void *injectedName = checkedTextMethod(objectClass, "GetName_Injected", 2, true, 0x01);
    const void *freeMethod = checkedTextMethod(allocatorClass, "Free", 1, true, 0x01);
    gTextByteClass = gTextApi.classFromName(gTextApi.corlib(), "System", "Byte");
    const void *spanParam = injectedName ? gTextApi.param(injectedName, 1) : nullptr;
    const void *spanBegin = spanClass ? gTextApi.field(spanClass, "begin") : nullptr;
    const void *spanLength = spanClass ? gTextApi.field(spanClass, "length") : nullptr;
    uint32_t align = 0;
    const size_t objectHeader = gTextApi.objectHeaderSize();
    gTextArrayHeader = gTextApi.arrayHeaderSize();
    const bool layout = spanClass && gTextApi.isValueType(spanClass) &&
        gTextApi.valueSize(spanClass, &align) == sizeof(NativeNameSpan) && align == alignof(NativeNameSpan) &&
        spanBegin && spanLength && gTextApi.fieldOffset(spanBegin) == objectHeader &&
        gTextApi.fieldOffset(spanLength) == objectHeader+offsetof(NativeNameSpan, length) &&
        gTextApi.typeKind(gTextApi.fieldType(spanBegin)) == 0x0f &&
        gTextApi.typeKind(gTextApi.fieldType(spanLength)) == 0x08 &&
        gTextArrayHeader >= 32 && gTextArrayHeader <= 128 && gTextArrayHeader % 8 == 0;
    const bool abi = layout && gTextByteClass && textParamKind(injectedBytes, 0, 0x18) &&
        textParamKind(injectedName, 0, 0x18) && spanParam && gTextApi.isByref(spanParam) &&
        gTextApi.classFromType(spanParam) == spanClass && textParamKind(freeMethod, 0, 0x0f) &&
        gTextApi.elementClass(gTextApi.classFromType(gTextApi.returnType(injectedBytes))) == gTextByteClass;
    gTextReadReturn = textGetterBytesReturn(textMethodEntry(textMethod), textMethodEntry(bytesMethod));
    void *entry = abi && gTextReadReturn ? unityIcall("UnityEngine.TextAsset::get_bytes_Injected(System.IntPtr)") : nullptr;
    void *nameEntry = abi ? unityIcall("UnityEngine.Object::GetName_Injected(System.IntPtr,UnityEngine.Bindings.ManagedSpanWrapper&)") : nullptr;
    void *freeEntry = abi ? unityIcall("UnityEngine.Bindings.BindingsAllocator::Free(System.Void*)") : nullptr;
    if (!entry || !nameEntry || !freeEntry || entry == nameEntry || entry == freeEntry) {
        const bool exhausted = gTextInstallAttempts >= 64;
        if (gTextInstallAttempts == 1 || exhausted)
            breadcrumb("WARN", "core.textasset.unavailable", "route=unity_bytes_text_only attempt=%u abi=%d caller=%d bytes=%d name=%d free=%d",
                gTextInstallAttempts, abi, gTextReadReturn != 0, entry != nullptr, nameEntry != nullptr, freeEntry != nullptr);
        gTextInstallState.store(exhausted ? 3 : 0, std::memory_order_release);
        return;
    }
    gNativeGetName = reinterpret_cast<NativeGetName>(nameEntry);
    gNativeNameFree = reinterpret_cast<NativeFree>(freeEntry);
    void *trampoline = nullptr;
    const bool installed = installHook(entry, reinterpret_cast<void *>(hookedTextAssetBytes), &trampoline,
        "Unity TextAsset bytes icall (text-only display resources)");
    if (!installed) {
        const int restored = DobbyDestroy(entry);
        breadcrumb("WARN", "core.textasset.rollback", "destroy_result=%d", restored);
    }
    gTextOriginal.store(reinterpret_cast<TextBytesGetter>(installed ? trampoline : entry), std::memory_order_release);
    gTextInstallState.store(installed ? 2 : 3, std::memory_order_release);
    breadcrumb(installed ? "INFO" : "WARN", "core.textasset.ready", "installed=%d route=unity_bytes_text_only resources=5 il2cpp_code_modified=0 caller=+0x%lx",
        installed, static_cast<unsigned long>(gTextReadReturn-gTextImage.base));
}

// The normal font and localization readers supply a real runtime context.
// A loaded ELF is not proof that IL2CPP has initialized: domain_get itself can
// allocate the domain in Unity, so it must never be used as a worker readiness poll.
void armTextAssetBootstrap() {
    if (!gTextPrepared) return;
    // Dobby's process-wide entry list is not synchronized. Arm only after all
    // setup-worker font hooks finish; the callback CAS serializes later installs.
    gTextConfigured.store(true, std::memory_order_release);
    breadcrumb("INFO", "core.textasset.bootstrap.armed", "Setup hooks complete; waiting for attached game callback");
}

void observeTextAssetBootstrap(const char *source) {
    if (!gTextConfigured.load(std::memory_order_acquire) || gInTextInstall ||
        gTextInstallState.load(std::memory_order_acquire) != 0 || !gTextApi.currentThread()) return;
    if (gBootstrapObserved.fetch_add(1, std::memory_order_relaxed) < 4)
        breadcrumb("INFO", "core.textasset.bootstrap.trigger", "source=%s attached=1", source);
    tryInstallTextAssetHook();
}

void configureTextAssetHook(const UnitySearch &image) {
    const char *indexFailure = nullptr;
    int indexErrno = 0;
    const auto indexText = readTextAssetFile(std::string(kRuntimeLocalize) + "/.lcpatch-text-assets.tsv", 4U * 1024U * 1024U, &indexFailure, &indexErrno);
    gTextIndex = lcpatch::parseAssetIndex(indexText);
    if (gTextIndex.empty()) {
        breadcrumb("WARN", "core.textasset.unavailable", "index_unavailable reason=%s errno=%d; repair applied pack in LCPatch and restart game",
            indexText.empty() ? indexFailure : "parse_empty", indexErrno);
        return;
    }
    gTextImage = image;
    gTextUnityImage = findImage(kUnityName);
#define TEXT_API(field, name) \
    gTextApi.field = reinterpret_cast<decltype(gTextApi.field)>(imageExport(image, name)); \
    if (!gTextApi.field) { breadcrumb("WARN", "core.textasset.unavailable", "Missing export: %s", name); return; }
    TEXT_API(domainGet, "il2cpp_domain_get");
    TEXT_API(assemblies, "il2cpp_domain_get_assemblies");
    TEXT_API(assemblyImage, "il2cpp_assembly_get_image");
    TEXT_API(corlib, "il2cpp_get_corlib");
    TEXT_API(classFromType, "il2cpp_class_from_il2cpp_type");
    TEXT_API(elementClass, "il2cpp_class_get_element_class");
    TEXT_API(param, "il2cpp_method_get_param");
    TEXT_API(isByref, "il2cpp_type_is_byref");
    TEXT_API(isValueType, "il2cpp_class_is_valuetype");
    TEXT_API(valueSize, "il2cpp_class_value_size");
    TEXT_API(field, "il2cpp_class_get_field_from_name");
    TEXT_API(fieldOffset, "il2cpp_field_get_offset");
    TEXT_API(fieldType, "il2cpp_field_get_type");
    TEXT_API(objectHeaderSize, "il2cpp_object_header_size");
    TEXT_API(arrayHeaderSize, "il2cpp_array_object_header_size");
    TEXT_API(arrayNew, "il2cpp_array_new");
    TEXT_API(arrayLength, "il2cpp_array_length");
    TEXT_API(arrayBytes, "il2cpp_array_get_byte_length");
    TEXT_API(classFromName, "il2cpp_class_from_name");
    TEXT_API(method, "il2cpp_class_get_method_from_name");
    TEXT_API(flags, "il2cpp_method_get_flags");
    TEXT_API(paramCount, "il2cpp_method_get_param_count");
    TEXT_API(returnType, "il2cpp_method_get_return_type");
    TEXT_API(typeKind, "il2cpp_type_get_type");
    TEXT_API(currentThread, "il2cpp_thread_current");
#undef TEXT_API
    // Resolve through the public API without modifying its code or its shared
    // implementation. Generated wrappers need not pass through this export:
    // the narrow Unity bytes icall is installed from a real game callback.
    void *resolver = imageExport(image, "il2cpp_resolve_icall");
    if (!resolver) {
        breadcrumb("WARN", "core.textasset.unavailable", "Missing export: il2cpp_resolve_icall");
        return;
    }
    gResolveIcall.store(reinterpret_cast<ResolveIcall>(resolver), std::memory_order_release);
    gTextPrepared = true;
    breadcrumb("INFO", "core.textasset.bootstrap", "mode=existing_callbacks waiting=1 indexed=%zu resolver_unmodified=1", gTextIndex.size());
}
