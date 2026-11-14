// Included inside the core's anonymous namespace after ELF/image helpers.
// Bootstrap only on a real Resources/TextAsset icall on the game's managed thread.
// No runtime_invoke hook, worker-thread managed allocation, or fixed game RVA.
struct TextAssetApi {
    void *(*domainGet)() = nullptr;
    const void **(*assemblies)(void *, size_t *) = nullptr;
    const void *(*assemblyImage)(const void *) = nullptr;
    void *(*classFromName)(const void *, const char *, const char *) = nullptr;
    const void *(*method)(void *, const char *, int) = nullptr;
    uint32_t (*flags)(const void *, uint32_t *) = nullptr;
    uint32_t (*paramCount)(const void *) = nullptr;
    const void *(*returnType)(const void *) = nullptr;
    int (*typeKind)(const void *) = nullptr;
    void *(*currentThread)() = nullptr;
    int32_t (*stringLength)(void *) = nullptr;
    const uint16_t *(*stringChars)(void *) = nullptr;
    void *(*stringNewLen)(const char *, uint32_t) = nullptr;
};
TextAssetApi gTextApi{};
UnitySearch gTextImage{};
UnitySearch gTextUnityImage{};
using ResolveIcall = void *(*)(const char *);
using TextGetter = void *(*)(void *, const void *);
using IsMainThread = bool (*)();
std::atomic<ResolveIcall> gResolveIcall{nullptr};
IsMainThread gTextIsMainThread = nullptr;
std::atomic<TextGetter> gTextOriginal{nullptr};
TextGetter gAssetName = nullptr;
const void *gAssetNameMethod = nullptr;
std::atomic<unsigned> gTextInstallState{0};
std::atomic<unsigned> gTextSwaps{0};
std::atomic<unsigned> gIcallObserved{0};
std::atomic<unsigned> gTextMisses{0};
std::atomic<unsigned> gTextThreadSkips{0};
std::atomic<unsigned> gResolverBootstrapState{0}; // waiting, installing, installed, unavailable
unsigned gTextInstallAttempts = 0; // installer state 1 owns this counter
thread_local bool gInTextInstall = false;
std::unordered_map<std::string, std::string> gTextIndex;

// Dobby activates the patched entry before writing its out-origin pointer.
// Publish an installer-local trampoline only after Dobby returns; callbacks
// wait at this native boundary without taking IL2CPP or logging locks.
template <typename Fn>
Fn publishedOriginal(const std::atomic<Fn> &slot) {
    Fn target = slot.load(std::memory_order_acquire);
    while (!target) { sched_yield(); target = slot.load(std::memory_order_acquire); }
    return target;
}

std::string readTextAssetFile(const std::string &path, size_t maximum) {
    const int fd = static_cast<int>(syscall(SYS_openat, AT_FDCWD, path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW));
    if (fd < 0) return {};
    struct stat info{};
    std::string text;
    if (fstat(fd, &info) == 0 && S_ISREG(info.st_mode) && info.st_size > 0 &&
        static_cast<uint64_t>(info.st_size) <= maximum) {
        text.resize(static_cast<size_t>(info.st_size));
        size_t position = 0;
        while (position < text.size()) {
            const auto count = syscall(SYS_read, fd, &text[position], text.size() - position);
            if (count <= 0) { text.clear(); break; }
            position += static_cast<size_t>(count);
        }
    }
    syscall(SYS_close, fd);
    return text;
}

void *hookedTextAsset(void *object, const void *method) {
    const auto original = publishedOriginal(gTextOriginal);
    if (!object || gInTextInstall) return original(object, method);
    if (!gTextApi.currentThread() || !gTextIsMainThread || !gTextIsMainThread()) {
        if (gTextThreadSkips.fetch_add(1, std::memory_order_relaxed) < 4)
            breadcrumb("WARN", "core.textasset.thread_skip", "Background TextAsset reader retained; name access requires Unity main thread");
        return original(object, method);
    }
    if (gVerificationReady.load(std::memory_order_acquire) &&
        lcpatch::isResourceVerificationRead(gVerificationRange, gVerificationImage))
        return original(object, method);
    void *name = gAssetName(object, gAssetNameMethod);
    if (!name) return original(object, method);
    const int32_t length = gTextApi.stringLength(name);
    if (length <= 0 || length > 1024) return original(object, method);
    const uint16_t *chars = gTextApi.stringChars(name);
    std::string key;
    key.reserve(static_cast<size_t>(length));
    for (int32_t i = 0; i < length; ++i) {
        // Game resource identifiers are ASCII; never guess an unknown name.
        if (chars[i] < 32 || chars[i] > 126) return original(object, method);
        key.push_back(static_cast<char>(chars[i]));
    }
    const auto selected = gTextIndex.find(lcpatch::assetKey(key));
    if (selected == gTextIndex.end()) {
        if (gTextMisses.fetch_add(1, std::memory_order_relaxed) < 8)
            breadcrumb("INFO", "core.textasset.lookup_miss", "asset=%s; no matching applied resource", key.c_str());
        return original(object, method);
    }
    const auto text = readTextAssetFile(std::string(kRuntimeLocalize) + "/" + selected->second, 8U * 1024U * 1024U);
    const auto first = text.find_first_not_of(" \t\r\n");
    if (first == std::string::npos || (text[first] != '{' && text[first] != '[') || text.find('\0') != std::string::npos)
        return original(object, method);
    void *replacement = gTextApi.stringNewLen(text.data(), static_cast<uint32_t>(text.size()));
    if (!replacement) return original(object, method);
    const unsigned count = gTextSwaps.fetch_add(1, std::memory_order_relaxed) + 1;
    if (count <= 16 || count % 128 == 0)
        breadcrumb("INFO", "core.textasset.redirect", "asset=%s file=%s count=%u", key.c_str(), selected->second.c_str(), count);
    return replacement;
}

void *checkedTextGetter(void *klass, const char *name, const void **metadata) {
    if (!klass) return nullptr;
    const void *method = gTextApi.method(klass, name, 0);
    if (!method) return nullptr;
    uint32_t implementation = 0;
    const void *type = gTextApi.returnType(method);
    if (gTextApi.paramCount(method) != 0 || (gTextApi.flags(method, &implementation) & 0x10U) ||
        !type || gTextApi.typeKind(type) != 0x0e) return nullptr; // instance string ()
    void *entry = nullptr;
    std::memcpy(&entry, method, sizeof(entry)); // Unity IL2CPP MethodInfo::methodPointer
    if (!rangeContains(gTextImage.executable, gTextImage.count, reinterpret_cast<uintptr_t>(entry), 16)) return nullptr;
    if (metadata) *metadata = method;
    return entry;
}

void tryInstallTextAssetHook() {
    if (gInTextInstall || !gTextApi.currentThread()) return;
    unsigned expected = 0;
    if (!gTextInstallState.compare_exchange_strong(expected, 1, std::memory_order_acq_rel)) return;
    gInTextInstall = true;
    // Object.get_name is a Unity main-thread API. Resolve Unity's explicitly
    // thread-safe predicate rather than guessing Android thread names or IDs.
    void *mainCheck = publishedOriginal(gResolveIcall)("UnityEngine.Object::CurrentThreadIsMainThread()");
    if (!rangeContains(gTextUnityImage.executable, gTextUnityImage.count, reinterpret_cast<uintptr_t>(mainCheck), 4)) {
        breadcrumb("WARN", "core.textasset.unavailable", "Unity main-thread predicate unavailable; original assets retained");
        gTextInstallState.store(3, std::memory_order_release);
        gInTextInstall = false;
        return;
    }
    gTextIsMainThread = reinterpret_cast<IsMainThread>(mainCheck);
    if (!gTextIsMainThread()) {
        gTextInstallState.store(0, std::memory_order_release);
        gInTextInstall = false;
        return;
    }
    ++gTextInstallAttempts;
    void *domain = gTextApi.domainGet();
    size_t count = 0;
    const void **assemblies = domain ? gTextApi.assemblies(domain, &count) : nullptr;
    void *textClass = nullptr, *objectClass = nullptr;
    if (assemblies && count < 4096) for (size_t i = 0; i < count; ++i) {
        const void *image = gTextApi.assemblyImage(assemblies[i]);
        if (!image) continue;
        if (!textClass) textClass = gTextApi.classFromName(image, "UnityEngine", "TextAsset");
        if (!objectClass) objectClass = gTextApi.classFromName(image, "UnityEngine", "Object");
        if (textClass && objectClass) break;
    }
    void *entry = checkedTextGetter(textClass, "get_text", nullptr);
    void *nameEntry = checkedTextGetter(objectClass, "get_name", &gAssetNameMethod);
    if (entry && nameEntry && entry != nameEntry) {
        gAssetName = reinterpret_cast<TextGetter>(nameEntry);
        void *trampoline = nullptr;
        const bool installed = installHook(entry, reinterpret_cast<void *>(hookedTextAsset), &trampoline,
            "UnityEngine.TextAsset.get_text");
        // Failed routing is restored before publishing the original entry, so
        // every installation outcome releases any callback already in flight.
        if (!installed) DobbyDestroy(entry);
        gTextOriginal.store(reinterpret_cast<TextGetter>(installed ? trampoline : entry), std::memory_order_release);
        gTextInstallState.store(installed ? 2 : 3, std::memory_order_release);
        breadcrumb(installed ? "INFO" : "WARN", "core.textasset.ready", "installed=%d indexed=%zu", installed, gTextIndex.size());
    } else {
        const bool exhausted = gTextInstallAttempts >= 64;
        if (gTextInstallAttempts == 1 || exhausted)
            breadcrumb("WARN", "core.textasset.unavailable", "attempt=%u text_class=%d object_class=%d getter=%d name_getter=%d",
                gTextInstallAttempts, textClass != nullptr, objectClass != nullptr, entry != nullptr, nameEntry != nullptr);
        gTextInstallState.store(exhausted ? 3 : 0, std::memory_order_release);
    }
    gInTextInstall = false;
}

void *hookedResolveIcall(const char *name) {
    void *result = publishedOriginal(gResolveIcall)(name);
    if (name && (std::strstr(name, "UnityEngine.ResourcesAPIInternal::Load") == name ||
                 std::strstr(name, "UnityEngine.AssetBundle::LoadAsset") == name ||
                 std::strstr(name, "UnityEngine.TextAsset::") == name)) {
        if (gIcallObserved.fetch_add(1, std::memory_order_relaxed) < 4)
            breadcrumb("INFO", "core.textasset.bootstrap.trigger", "name=%s", name);
        tryInstallTextAssetHook();
    }
    return result;
}

lcpatch::SharedIcallEntry sharedIcallEntry(uintptr_t publicEntry) {
    return lcpatch::findSharedIcallResolver(publicEntry, [](uintptr_t address, uint32_t &instruction) {
        if (!rangeContains(gTextImage.executable, gTextImage.count, address, 16) ||
            !rangeContains(gTextImage.readable, gTextImage.readableCount, address, 16))
            return false;
        std::memcpy(&instruction, reinterpret_cast<const void *>(address), sizeof(instruction));
        // Protected game code can map before its entry is decrypted. Do not
        // install a hook on UDF/zero bytes or block the font worker waiting for it.
        return true;
    });
}

bool installSharedIcallObserver() {
    void *exportEntry = imageExport(gTextImage, "il2cpp_resolve_icall");
    if (!exportEntry) {
        breadcrumb("WARN", "core.textasset.unavailable", "Missing export: il2cpp_resolve_icall");
        gResolverBootstrapState.store(3, std::memory_order_release);
        return false;
    }
    const auto selected = sharedIcallEntry(reinterpret_cast<uintptr_t>(exportEntry));
    if (!selected.entry) {
        if (!selected.pending) {
            breadcrumb("WARN", "core.textasset.unavailable", "reason=%s; public-export-only fallback is not used", selected.reason);
            gResolverBootstrapState.store(3, std::memory_order_release);
        }
        return false;
    }
    void *trampoline = nullptr;
    void *target = reinterpret_cast<void *>(selected.entry);
    const bool installed = installHook(target, reinterpret_cast<void *>(hookedResolveIcall), &trampoline,
        "InternalCalls.Resolve (TextAsset bootstrap)");
    if (!installed) DobbyDestroy(target);
    gResolveIcall.store(reinterpret_cast<ResolveIcall>(installed ? trampoline : target), std::memory_order_release);
    gResolverBootstrapState.store(installed ? 2 : 3, std::memory_order_release);
    breadcrumb(installed ? "INFO" : "WARN", "core.textasset.bootstrap", "mode=shared_resolver waiting=%d indexed=%zu public=+0x%lx implementation=+0x%lx",
        installed, gTextIndex.size(), static_cast<unsigned long>(reinterpret_cast<uintptr_t>(exportEntry) - gTextImage.base),
        static_cast<unsigned long>(selected.entry - gTextImage.base));
    return installed;
}

void *waitForIcallResolver(void *) {
    for (unsigned attempt = 0; attempt < 120; ++attempt) {
        if (installSharedIcallObserver() || gResolverBootstrapState.load(std::memory_order_acquire) == 3) return nullptr;
        usleep(25000);
    }
    gResolverBootstrapState.store(3, std::memory_order_release);
    breadcrumb("WARN", "core.textasset.unavailable", "reason=resolver_code_not_ready after 3000 ms; original assets retained");
    return nullptr;
}

void configureTextAssetHook(const UnitySearch &image) {
    gTextIndex = lcpatch::parseAssetIndex(readTextAssetFile(std::string(kRuntimeLocalize) + "/.lcpatch-text-assets.tsv", 4U * 1024U * 1024U));
    if (gTextIndex.empty()) {
        breadcrumb("WARN", "core.textasset.unavailable", "No resource index; reapply translation in LCPatch");
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
    TEXT_API(classFromName, "il2cpp_class_from_name");
    TEXT_API(method, "il2cpp_class_get_method_from_name");
    TEXT_API(flags, "il2cpp_method_get_flags");
    TEXT_API(paramCount, "il2cpp_method_get_param_count");
    TEXT_API(returnType, "il2cpp_method_get_return_type");
    TEXT_API(typeKind, "il2cpp_type_get_type");
    TEXT_API(currentThread, "il2cpp_thread_current");
    TEXT_API(stringLength, "il2cpp_string_length");
    TEXT_API(stringChars, "il2cpp_string_chars");
    TEXT_API(stringNewLen, "il2cpp_string_new_len");
#undef TEXT_API
    // Generated IL2CPP wrappers resolve icalls through InternalCalls::Resolve
    // directly, bypassing the exported il2cpp_resolve_icall veneer. Observe the
    // validated common implementation, not only callers of the public export.
    gResolverBootstrapState.store(1, std::memory_order_release);
    if (installSharedIcallObserver() || gResolverBootstrapState.load(std::memory_order_acquire) == 3) return;
    pthread_t worker{};
    if (pthread_create(&worker, nullptr, waitForIcallResolver, nullptr) == 0) {
        pthread_detach(worker);
        breadcrumb("INFO", "core.textasset.bootstrap.wait", "Waiting for protected resolver code; font and file setup continue");
    } else {
        gResolverBootstrapState.store(3, std::memory_order_release);
        breadcrumb("WARN", "core.textasset.unavailable", "reason=resolver_worker_creation_failed");
    }
}
