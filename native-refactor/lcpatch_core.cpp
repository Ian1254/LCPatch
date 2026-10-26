#include <android/log.h>
#include <dlfcn.h>
#include <elf.h>
#include <fcntl.h>
#include <link.h>
#include <pthread.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>

#include <array>
#include <atomic>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#include "dobby.h"
#include "managed_localize.h"

namespace {

constexpr char kTag[] = "LCPatchCore";
#ifndef LCPATCH_TEST_LOCALIZE_DIRECTORY
#define LCPATCH_TEST_LOCALIZE_DIRECTORY "/storage/emulated/0/Android/data/com.ProjectMoon.LimbusCompany/cache/Localize/cn"
#endif
constexpr char kRuntimeLocalize[] = LCPATCH_TEST_LOCALIZE_DIRECTORY;
constexpr char kRuntimeFont[] = "/sdcard/Android/data/com.ProjectMoon.LimbusCompany/cache/Localize/cn/ChineseFont.ttf";
constexpr char kNativeLog[] = "/storage/emulated/0/Android/data/com.ProjectMoon.LimbusCompany/cache/lcpatch-native.log";
constexpr char kUnityName[] = "libunity.so";
constexpr char kIl2CppName[] = "libil2cpp.so";
constexpr uintptr_t kBuildIdNote = 0x308;
constexpr size_t kMaxFontBytes = 64U * 1024U * 1024U;
constexpr size_t kMaxSegments = 16;

constexpr std::array<uint8_t, 16> kBuildIdNoteHeader = {
    0x04, 0x00, 0x00, 0x00, 0x08, 0x00, 0x00, 0x00,
    0x03, 0x00, 0x00, 0x00, 0x47, 0x4e, 0x55, 0x00,
};
constexpr std::array<uint8_t, 32> kExpectedEntry = {
    0xff, 0x43, 0x02, 0xd1, 0xfe, 0x2b, 0x00, 0xf9,
    0xf8, 0x5f, 0x06, 0xa9, 0xf6, 0x57, 0x07, 0xa9,
    0xf4, 0x4f, 0x08, 0xa9, 0x98, 0x63, 0x00, 0xf0,
    0xf5, 0x03, 0x03, 0x2a, 0xf4, 0x03, 0x02, 0x2a,
};
constexpr std::array<uint8_t, 12> kExpectedAccessor = {
    0x08, 0x1c, 0x40, 0xf9, 0x00, 0x01, 0x02, 0x91,
    0xc0, 0x03, 0x5f, 0xd6,
};
struct UnityProfile {
    const char *name;
    std::array<uint8_t, 8> buildId;
    uintptr_t entry;
    uintptr_t accessor;
    std::array<uint8_t, 32> entrySignature;
    std::array<uint8_t, 12> accessorSignature;
};
// GNU note descriptor bytes are stored in displayed Build ID order (not an integer).
constexpr UnityProfile kUnityProfiles[] = {
    {"limbus-1.113.1", {0xff, 0x68, 0x15, 0x3c, 0xd3, 0x15, 0x01, 0xd4},
     0xB92F30, 0xB73E90, kExpectedEntry, kExpectedAccessor},
    {"limbus-1.115.0", {0x77, 0x19, 0x0d, 0x04, 0xcd, 0x31, 0x4f, 0x47},
     0xB93770, 0xB746D0, kExpectedEntry, kExpectedAccessor},
};
constexpr std::array<size_t, 4> kKnownOriginalFontSizes = {
    0x557F8U, 0xB2B80U, 0x2B173AU, 0x48345EU,
};

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, kTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, kTag, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kTag, __VA_ARGS__)

using FontFn = void (*)(void *, uint32_t, uint32_t, uint32_t);
FontFn gFontOriginal = nullptr;
uint8_t *gFontData = nullptr;
size_t gFontSize = 0;
std::atomic<unsigned> gSwapCount{0};
std::atomic<unsigned> gRedirectCount{0};
std::atomic<bool> gStarted{false};
pthread_mutex_t gLogLock = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t gHookLock = PTHREAD_MUTEX_INITIALIZER;

void breadcrumb(const char *level, const char *code, const char *format, ...) {
    char message[1024]{};
    va_list args;
    va_start(args, format);
    std::vsnprintf(message, sizeof(message), format, args);
    va_end(args);
    timespec now{};
    clock_gettime(CLOCK_REALTIME, &now);
    char line[1280]{};
    const int length = std::snprintf(line, sizeof(line), "%lld|%s|%s|%s\n",
        static_cast<long long>(now.tv_sec) * 1000LL + now.tv_nsec / 1000000LL,
        level, code, message);
    if (length <= 0) return;
    pthread_mutex_lock(&gLogLock);
    const int fd = static_cast<int>(syscall(SYS_openat, AT_FDCWD, kNativeLog,
        O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0644));
    if (fd >= 0) {
        const size_t count = static_cast<size_t>(length) < sizeof(line) ?
            static_cast<size_t>(length) : sizeof(line) - 1;
        syscall(SYS_write, fd, line, count);
        syscall(SYS_close, fd);
    }
    pthread_mutex_unlock(&gLogLock);
}

bool installHook(void *target, void *replacement, void **original, const char *name);

bool installHook(void *target, void *replacement, void **original, const char *name) {
    if (!target) { LOGE("symbol missing: %s", name); return false; }
    // Dobby's interceptor registry is not thread-safe. Font and text workers
    // may become ready together, so serialize every installation.
    pthread_mutex_lock(&gHookLock);
    const int result = DobbyHook(target, replacement, original);
    pthread_mutex_unlock(&gHookLock);
    if (result != 0 || !*original) { LOGE("hook failed: %s (%d)", name, result); return false; }
    return true;
}

struct Segment { uintptr_t begin; uintptr_t end; int protection; };
struct UnitySearch {
    uintptr_t base = 0;
    Segment readable[kMaxSegments]{};
    size_t readableCount = 0;
    Segment executable[kMaxSegments]{};
    size_t count = 0;
};
struct ImageRequest { const char *suffix; UnitySearch result; };

int findImageCallback(dl_phdr_info *info, size_t, void *opaque) {
    if (!info || !info->dlpi_name) return 0;
    auto *request = static_cast<ImageRequest *>(opaque);
    const size_t nameLength = std::strlen(info->dlpi_name);
    const size_t suffixLength = std::strlen(request->suffix);
    if (nameLength < suffixLength ||
        std::memcmp(info->dlpi_name + nameLength - suffixLength, request->suffix, suffixLength) != 0) return 0;
    auto *result = &request->result;
    result->base = static_cast<uintptr_t>(info->dlpi_addr);
    for (ElfW(Half) i = 0; i < info->dlpi_phnum; ++i) {
        const auto &header = info->dlpi_phdr[i];
        if (header.p_type != PT_LOAD || !(header.p_flags & PF_R)) continue;
        int protection = PROT_READ;
        if (header.p_flags & PF_W) protection |= PROT_WRITE;
        if (header.p_flags & PF_X) protection |= PROT_EXEC;
        if (result->readableCount < kMaxSegments) result->readable[result->readableCount++] = {
            result->base + header.p_vaddr, result->base + header.p_vaddr + header.p_memsz, protection };
        if ((header.p_flags & PF_X) && result->count < kMaxSegments) result->executable[result->count++] = {
            result->base + header.p_vaddr, result->base + header.p_vaddr + header.p_memsz, protection };
    }
    return 1;
}

UnitySearch findImage(const char *suffix) {
    ImageRequest request{suffix, {}};
    dl_iterate_phdr(findImageCallback, &request);
    return request.result;
}

template <size_t N>
bool memoryMatches(uintptr_t base, uintptr_t offset, const std::array<uint8_t, N> &expected) {
    return std::memcmp(reinterpret_cast<const void *>(base + offset), expected.data(), N) == 0;
}

bool rangeContains(const Segment *segments, size_t count, uintptr_t address, size_t length) {
    if (length > UINTPTR_MAX - address) return false;
    for (size_t i = 0; i < count; ++i) {
        if (address >= segments[i].begin && address + length <= segments[i].end) return true;
    }
    return false;
}

uintptr_t loadedAddress(const UnitySearch &image, Elf64_Addr value, size_t length) {
    const uintptr_t raw = static_cast<uintptr_t>(value);
    if (rangeContains(image.readable, image.readableCount, raw, length)) return raw;
    if (raw <= UINTPTR_MAX - image.base) {
        const uintptr_t relative = image.base + raw;
        if (rangeContains(image.readable, image.readableCount, relative, length)) return relative;
    }
    return 0;
}

// Resolve public IL2CPP APIs in the already loaded image. Module class-loader
// namespaces need not permit dlopen("libil2cpp.so"). No fixed game offsets.
void *imageExport(const UnitySearch &image, const char *name) {
    const auto *elf = reinterpret_cast<const Elf64_Ehdr *>(image.base);
    const auto *headers = reinterpret_cast<const Elf64_Phdr *>(image.base + elf->e_phoff);
    Elf64_Addr symbols = 0, strings = 0, hash = 0;
    size_t stringBytes = 0;
    for (Elf64_Half i = 0; i < elf->e_phnum; ++i) {
        if (headers[i].p_type != PT_DYNAMIC) continue;
        const uintptr_t at = image.base + headers[i].p_vaddr;
        if (!rangeContains(image.readable, image.readableCount, at, headers[i].p_memsz)) return nullptr;
        const auto *entries = reinterpret_cast<const Elf64_Dyn *>(at);
        for (size_t n = 0; n < headers[i].p_memsz / sizeof(Elf64_Dyn) && entries[n].d_tag != DT_NULL; ++n) {
            switch (entries[n].d_tag) {
                case DT_SYMTAB: symbols = entries[n].d_un.d_ptr; break;
                case DT_STRTAB: strings = entries[n].d_un.d_ptr; break;
                case DT_STRSZ: stringBytes = entries[n].d_un.d_val; break;
                case DT_GNU_HASH: hash = entries[n].d_un.d_ptr; break;
                default: break;
            }
        }
    }
    const uintptr_t symbolsAt = loadedAddress(image, symbols, sizeof(Elf64_Sym));
    const uintptr_t stringsAt = loadedAddress(image, strings, stringBytes);
    const uintptr_t hashAt = loadedAddress(image, hash, 16);
    if (!symbolsAt || !stringsAt || !hashAt || !stringBytes) return nullptr;
    const auto *gnu = reinterpret_cast<const uint32_t *>(hashAt);
    const uint32_t buckets = gnu[0], first = gnu[1], bloom = gnu[2];
    if (!buckets || buckets > (1U << 20) || bloom > (1U << 20)) return nullptr;
    const uintptr_t bucketsAt = hashAt + 16 + static_cast<size_t>(bloom) * sizeof(Elf64_Addr);
    if (!rangeContains(image.readable, image.readableCount, bucketsAt, static_cast<size_t>(buckets) * 4)) return nullptr;
    uint32_t value = 5381;
    for (const auto *p = reinterpret_cast<const unsigned char *>(name); *p; ++p) value = value * 33 + *p;
    uint32_t index = reinterpret_cast<const uint32_t *>(bucketsAt)[value % buckets];
    if (index < first) return nullptr;
    const uintptr_t chainsAt = bucketsAt + static_cast<size_t>(buckets) * 4;
    for (unsigned n = 0; n < (1U << 20); ++n, ++index) {
        const uintptr_t chainAt = chainsAt + static_cast<size_t>(index - first) * 4;
        const uintptr_t symbolAt = symbolsAt + static_cast<size_t>(index) * sizeof(Elf64_Sym);
        if (!rangeContains(image.readable, image.readableCount, chainAt, 4) ||
            !rangeContains(image.readable, image.readableCount, symbolAt, sizeof(Elf64_Sym))) return nullptr;
        const uint32_t chain = *reinterpret_cast<const uint32_t *>(chainAt);
        const auto &symbol = *reinterpret_cast<const Elf64_Sym *>(symbolAt);
        if ((chain | 1U) == (value | 1U) && symbol.st_name < stringBytes) {
            const char *candidate = reinterpret_cast<const char *>(stringsAt + symbol.st_name);
            if (std::memchr(candidate, '\0', stringBytes - symbol.st_name) &&
                std::strcmp(candidate, name) == 0 && symbol.st_shndx != SHN_UNDEF &&
                ELF64_ST_TYPE(symbol.st_info) == STT_FUNC) {
                const uintptr_t target = image.base + symbol.st_value;
                return rangeContains(image.executable, image.count, target, 4) ? reinterpret_cast<void *>(target) : nullptr;
            }
        }
        if (chain & 1U) break;
    }
    return nullptr;
}

struct LocalizeApi {
    void *(*domainGet)();
    const void **(*assemblies)(void *, size_t *);
    const void *(*assemblyImage)(const void *);
    void *(*classFromName)(const void *, const char *, const char *);
    const void *(*methods)(void *, void **);
    const char *(*methodName)(const void *);
    uint32_t (*paramCount)(const void *);
    const void *(*param)(const void *, uint32_t);
    const void *(*returnType)(const void *);
    uint32_t (*flags)(const void *, uint32_t *);
    int (*typeKind)(const void *);
    void *(*classFromType)(const void *);
    const char *(*className)(void *);
    const char *(*classNamespace)(void *);
    int32_t (*valueSize)(void *, uint32_t *);
    void *(*currentThread)();
};

bool loadLocalizeApi(const UnitySearch &image, LocalizeApi &api) {
#define LOAD_API(field, symbol) \
    api.field = reinterpret_cast<decltype(api.field)>(imageExport(image, symbol)); \
    if (!api.field) return false
    LOAD_API(domainGet, "il2cpp_domain_get");
    LOAD_API(assemblies, "il2cpp_domain_get_assemblies");
    LOAD_API(assemblyImage, "il2cpp_assembly_get_image");
    LOAD_API(classFromName, "il2cpp_class_from_name");
    LOAD_API(methods, "il2cpp_class_get_methods");
    LOAD_API(methodName, "il2cpp_method_get_name");
    LOAD_API(paramCount, "il2cpp_method_get_param_count");
    LOAD_API(param, "il2cpp_method_get_param");
    LOAD_API(returnType, "il2cpp_method_get_return_type");
    LOAD_API(flags, "il2cpp_method_get_flags");
    LOAD_API(typeKind, "il2cpp_type_get_type");
    LOAD_API(classFromType, "il2cpp_class_from_type");
    LOAD_API(className, "il2cpp_class_get_name");
    LOAD_API(classNamespace, "il2cpp_class_get_namespace");
    LOAD_API(valueSize, "il2cpp_class_value_size");
    LOAD_API(currentThread, "il2cpp_thread_current");
#undef LOAD_API
    auto &managed = lcpatch::managedLocalize;
    managed.stringLength = reinterpret_cast<decltype(managed.stringLength)>(imageExport(image, "il2cpp_string_length"));
    managed.stringChars = reinterpret_cast<decltype(managed.stringChars)>(imageExport(image, "il2cpp_string_chars"));
    managed.stringNew = reinterpret_cast<decltype(managed.stringNew)>(imageExport(image, "il2cpp_string_new"));
    return managed.stringLength && managed.stringChars && managed.stringNew;
}

bool namedType(const LocalizeApi &api, const void *type, const char *space, const char *name) {
    void *klass = api.classFromType(type);
    return klass && std::strcmp(api.classNamespace(klass), space) == 0 && std::strcmp(api.className(klass), name) == 0;
}

// Signatures observed in Android 1.117.1 metadata and decrypted runtime code:
// instance string LoadLocalizeFileSync(string); instance void ProcessLoadAsync(int);
// static string ReadAllText(string); static Task<string> ReadAllTextAsync(string, CancellationToken).
enum class LocalizeMethod { SyncLoader, AsyncLoader, Read, ReadAsync };
void *localizeMethod(const UnitySearch &image, const LocalizeApi &api, void *klass,
                     const char *name, LocalizeMethod kind) {
    if (!klass) return nullptr;
    void *iterator = nullptr, *target = nullptr;
    for (unsigned n = 0; n < 4096; ++n) {
        const void *method = api.methods(klass, &iterator);
        if (!method) break;
        if (std::strcmp(api.methodName(method), name) != 0) continue;
        const bool readAsync = kind == LocalizeMethod::ReadAsync;
        const bool asyncLoader = kind == LocalizeMethod::AsyncLoader;
        const bool isStatic = kind == LocalizeMethod::Read || readAsync;
        uint32_t implementation = 0;
        if (((api.flags(method, &implementation) & 0x10U) != 0) != isStatic ||
            api.paramCount(method) != (readAsync ? 2U : 1U) ||
            api.typeKind(api.param(method, 0)) != (asyncLoader ? 0x08 : 0x0e)) continue;
        const void *result = api.returnType(method);
        if (api.typeKind(result) != (readAsync ? 0x15 : asyncLoader ? 0x01 : 0x0e)) continue;
        if (readAsync) {
            const void *token = api.param(method, 1);
            uint32_t alignment = 0;
            if (!namedType(api, token, "System.Threading", "CancellationToken") ||
                api.valueSize(api.classFromType(token), &alignment) != sizeof(lcpatch::CancellationToken) ||
                !namedType(api, result, "System.Threading.Tasks", "Task`1")) continue;
        }
        // Public MethodInfo ABI: methodPointer is its first field. Only accept
        // an aligned entry inside this exact loaded IL2CPP executable image.
        void *candidate = nullptr;
        std::memcpy(&candidate, method, sizeof(candidate));
        const auto address = reinterpret_cast<uintptr_t>(candidate);
        if ((address & 3U) || !rangeContains(image.executable, image.count, address, 16) || target) return nullptr;
        target = candidate;
    }
    return target;
}

void logLocalizeRedirect(const char *original, const char *selected) {
    const unsigned count = gRedirectCount.fetch_add(1, std::memory_order_relaxed);
    if (count < 12) breadcrumb("INFO", "core.localize.redirect", "%s -> %s", original, selected);
}

using RuntimeInvoke = void *(*)(const void *, void *, void **, void **);
RuntimeInvoke gInvokeOriginal = nullptr;
LocalizeApi gLocalizeApi{};
UnitySearch gLocalizeImage{};
std::atomic<bool> gInvokeReady{false};
std::atomic<unsigned> gLocalizeState{0}; // waiting, installing, ready, unsupported
unsigned gLocalizeAttempts = 0; // accessed only by the installer owning state 1

void tryInstallLocalize() {
    // This callback runs on the game's already attached managed thread, at an
    // actual public runtime_invoke boundary. Never call domain_get from the
    // startup worker: it can allocate GC objects before Runtime::Init is ready.
    if (!gLocalizeApi.currentThread()) return;
    unsigned expected = 0;
    if (!gLocalizeState.compare_exchange_strong(expected, 1, std::memory_order_acq_rel)) return;
    const auto &api = gLocalizeApi;
    const auto &image = gLocalizeImage;
    void *domain = api.domainGet();
    size_t count = 0;
    const void **assemblies = domain ? api.assemblies(domain, &count) : nullptr;
    void *manager = nullptr, *loader = nullptr, *file = nullptr;
    if (assemblies && count < 2048) {
        for (size_t i = 0; i < count; ++i) {
            const void *assembly = api.assemblyImage(assemblies[i]);
            if (!manager) manager = api.classFromName(assembly, "TextAssetPatch", "TextAssetPatchManager");
            if (!loader) loader = api.classFromName(assembly, "TextAssetPatch", "TextAssetAsyncLoader");
            if (!file) file = api.classFromName(assembly, "System.IO", "File");
        }
    }
    void *targets[] = {
        localizeMethod(image, api, manager, "LoadLocalizeFileSync", LocalizeMethod::SyncLoader),
        localizeMethod(image, api, loader, "ProcessLoadAsync", LocalizeMethod::AsyncLoader),
        localizeMethod(image, api, file, "ReadAllText", LocalizeMethod::Read),
        localizeMethod(image, api, file, "ReadAllTextAsync", LocalizeMethod::ReadAsync),
    };
    for (size_t i = 0; i < 4; ++i) {
        if (!targets[i]) {
            if (++gLocalizeAttempts < 120) gLocalizeState.store(0, std::memory_order_release);
            else {
                breadcrumb("WARN", "core.localize.unsupported", "reason=method_signature_unavailable index=%zu; original files retained", i);
                gLocalizeState.store(3, std::memory_order_release);
            }
            return;
        }
        for (size_t j = 0; j < i; ++j) if (targets[i] == targets[j]) {
            breadcrumb("WARN", "core.localize.unsupported", "reason=shared_method_entry; original files retained");
            gLocalizeState.store(3, std::memory_order_release);
            return;
        }
    }
    auto &managed = lcpatch::managedLocalize;
    managed.runtimeDirectory = kRuntimeLocalize;
    managed.onRedirect = logLocalizeRedirect;
    managed.installing.store(true, std::memory_order_release);
    if (!installHook(targets[2], reinterpret_cast<void *>(lcpatch::hookedReadText), reinterpret_cast<void **>(&managed.readText), "File.ReadAllText") ||
        !installHook(targets[3], reinterpret_cast<void *>(lcpatch::hookedReadTextAsync), reinterpret_cast<void **>(&managed.readTextAsync), "File.ReadAllTextAsync") ||
        !installHook(targets[0], reinterpret_cast<void *>(lcpatch::hookedLoadSync), reinterpret_cast<void **>(&managed.loadSync), "LoadLocalizeFileSync") ||
        !installHook(targets[1], reinterpret_cast<void *>(lcpatch::hookedLoadAsync), reinterpret_cast<void **>(&managed.loadAsync), "TextAssetAsyncLoader.ProcessLoadAsync")) {
        managed.installing.store(false, std::memory_order_release);
        gLocalizeState.store(3, std::memory_order_release);
        breadcrumb("ERROR", "core.localize.unsupported", "reason=hook_install_failed; original files retained");
        return;
    }
    managed.ready.store(true, std::memory_order_release);
    managed.installing.store(false, std::memory_order_release);
    gLocalizeState.store(2, std::memory_order_release);
    breadcrumb("INFO", "core.localize.hooked", "managed text loaders ready; filesystem reads unmodified");
}

void *hookedRuntimeInvoke(const void *method, void *object, void **arguments, void **exception) {
    while (!gInvokeReady.load(std::memory_order_acquire)) std::this_thread::yield();
    if (gLocalizeState.load(std::memory_order_acquire) == 0) tryInstallLocalize();
    return gInvokeOriginal(method, object, arguments, exception);
}

void *startLocalizeHook(void *) {
    UnitySearch image;
    for (unsigned attempt = 0; attempt < 120 && !image.base; ++attempt) {
        image = findImage(kIl2CppName);
        if (!image.base) usleep(250000);
    }
    if (!image.base || !loadLocalizeApi(image, gLocalizeApi)) {
        breadcrumb("WARN", "core.localize.unsupported", "reason=il2cpp_api_unavailable; original files retained");
        return nullptr;
    }
    gLocalizeImage = image;
    void *invoke = imageExport(image, "il2cpp_runtime_invoke");
    if (!installHook(invoke, reinterpret_cast<void *>(hookedRuntimeInvoke),
                     reinterpret_cast<void **>(&gInvokeOriginal), "il2cpp_runtime_invoke")) {
        breadcrumb("WARN", "core.localize.unsupported", "reason=runtime_entry_unavailable; original files retained");
        return nullptr;
    }
    gInvokeReady.store(true, std::memory_order_release);
    return nullptr;
}

bool validElfAndNote(const UnitySearch &image) {
    if (!image.base || !rangeContains(image.readable, image.readableCount, image.base, 6) ||
        !rangeContains(image.readable, image.readableCount, image.base + kBuildIdNote, 24)) return false;
    const auto *elf = reinterpret_cast<const uint8_t *>(image.base);
    return elf[0] == 0x7f && elf[1] == 'E' && elf[2] == 'L' && elf[3] == 'F' &&
           elf[4] == 2 && elf[5] == 1 && memoryMatches(image.base, kBuildIdNote, kBuildIdNoteHeader);
}

const UnityProfile *matchingProfile(const UnitySearch &image) {
    if (!validElfAndNote(image)) return nullptr;
    for (const auto &profile : kUnityProfiles) {
        if (memoryMatches(image.base, kBuildIdNote + kBuildIdNoteHeader.size(), profile.buildId))
            return &profile;
    }
    return nullptr;
}

bool verifiedUnity(const UnitySearch &image, const UnityProfile &profile) {
    return rangeContains(image.executable, image.count, image.base + profile.entry, profile.entrySignature.size()) &&
           rangeContains(image.executable, image.count, image.base + profile.accessor, profile.accessorSignature.size()) &&
           memoryMatches(image.base, profile.entry, profile.entrySignature) &&
           memoryMatches(image.base, profile.accessor, profile.accessorSignature);
}

// The only relocation-sensitive instruction in the known 32-byte entry fingerprint
// is ADRP x24 at +20. Keep its opcode and destination register; mask its page immediate.
bool entryFingerprint(uintptr_t address, uintptr_t segmentEnd) {
    if (address > segmentEnd || segmentEnd - address < kExpectedEntry.size()) return false;
    const auto *bytes = reinterpret_cast<const uint8_t *>(address);
    if (std::memcmp(bytes, kExpectedEntry.data(), 20) != 0 ||
        std::memcmp(bytes + 24, kExpectedEntry.data() + 24, 8) != 0) return false;
    uint32_t actual = 0, expected = 0;
    std::memcpy(&actual, bytes + 20, sizeof(actual));
    std::memcpy(&expected, kExpectedEntry.data() + 20, sizeof(expected));
    return (actual & 0x9f00001fU) == (expected & 0x9f00001fU);
}

bool isStackFrameStart(uint32_t instruction) { return (instruction & 0xffc003ffU) == 0xd10003ffU; }
bool savesLinkRegister(uint32_t instruction) {
    return (instruction & 0xffc003ffU) == 0xf90003feU || (instruction & 0xffc07fffU) == 0xa9007bfdU;
}
bool isReturn(uint32_t instruction) { return instruction == 0xd65f03c0U; }
uintptr_t branchTarget(uintptr_t pc, uint32_t instruction) {
    const int64_t delta = static_cast<int64_t>(static_cast<int32_t>(instruction << 6)) >> 4;
    return static_cast<uintptr_t>(static_cast<int64_t>(pc) + delta);
}

bool validatesCaller(uintptr_t call, uintptr_t segmentEnd) {
    if (call + 15 * sizeof(uint32_t) > segmentEnd) return false;
    const auto *after = reinterpret_cast<const uint32_t *>(call + sizeof(uint32_t));
    bool length = false, data = false, nullCheck = false;
    for (size_t i = 0; i < 14; ++i) {
        length |= (after[i] & 0xfffffc00U) == 0xf9400800U;
        data |= (after[i] & 0xfffffc00U) == 0xf9400000U;
        nullCheck |= (after[i] & 0x7e000000U) == 0x34000000U;
    }
    return length && data && nullCheck;
}

uintptr_t findFunctionStart(const Segment &segment, uintptr_t call) {
    const auto *words = reinterpret_cast<const uint32_t *>(segment.begin);
    const size_t callIndex = (call - segment.begin) / sizeof(uint32_t);
    const size_t floor = callIndex > 192 ? callIndex - 192 : 0;
    for (size_t i = callIndex; i-- > floor;) {
        if (isReturn(words[i])) break;
        if (!isStackFrameStart(words[i])) continue;
        const size_t limit = (i + 7 < callIndex) ? i + 7 : callIndex;
        for (size_t n = i + 1; n < limit; ++n) if (savesLinkRegister(words[n])) return segment.begin + i * sizeof(uint32_t);
    }
    return 0;
}

struct FontLocation {
    uintptr_t entry = 0;
    size_t accessors = 0;
    size_t callers = 0;
    size_t candidates = 0;
    const char *reason = nullptr;
};

FontLocation locateFontEntry(const UnitySearch &image) {
    FontLocation result{};
    uintptr_t accessor = 0;
    for (size_t s = 0; s < image.count; ++s) {
        const Segment &segment = image.executable[s];
        for (uintptr_t at = segment.begin; at <= segment.end &&
             segment.end - at >= kExpectedAccessor.size(); at += 4) {
            if (std::memcmp(reinterpret_cast<const void *>(at), kExpectedAccessor.data(), kExpectedAccessor.size()) == 0) {
                ++result.accessors;
                accessor = at;
                if (result.accessors > 1) { result.reason = "accessor_not_unique"; return result; }
            }
        }
    }
    if (result.accessors != 1) { result.reason = "accessor_not_unique"; return result; }
    for (size_t s = 0; s < image.count; ++s) {
        const Segment &segment = image.executable[s];
        for (uintptr_t at = segment.begin; at <= segment.end &&
             segment.end - at >= sizeof(uint32_t); at += 4) {
            const uint32_t instruction = *reinterpret_cast<const uint32_t *>(at);
            if ((instruction & 0xfc000000U) != 0x94000000U ||
                branchTarget(at, instruction) != accessor || !validatesCaller(at, segment.end)) continue;
            ++result.callers;
            if (result.callers > 1) { result.reason = "caller_not_unique"; return result; }
            const uintptr_t start = findFunctionStart(segment, at);
            if (start) { ++result.candidates; result.entry = start; }
        }
    }
    if (result.callers != 1) { result.reason = "caller_not_unique"; return result; }
    if (result.candidates != 1) { result.reason = "candidate_not_unique"; return result; }
    bool executable = false, fingerprint = false;
    for (size_t s = 0; s < image.count; ++s) {
        const Segment &segment = image.executable[s];
        if (result.entry < segment.begin || result.entry >= segment.end) continue;
        executable = true;
        fingerprint = entryFingerprint(result.entry, segment.end);
        break;
    }
    if (!executable) result.reason = "candidate_not_executable";
    else if (!fingerprint) result.reason = "entry_signature_mismatch";
    return result;
}

bool loadFont() {
    FILE *file = std::fopen(kRuntimeFont, "rb");
    if (!file) { LOGE("font missing: %s", kRuntimeFont); breadcrumb("ERROR", "core.font.missing", "%s", kRuntimeFont); return false; }
    if (std::fseek(file, 0, SEEK_END) != 0) { std::fclose(file); return false; }
    const long length = std::ftell(file);
    if (length < 12 || static_cast<unsigned long>(length) > kMaxFontBytes || std::fseek(file, 0, SEEK_SET) != 0) {
        std::fclose(file); LOGE("invalid font size: %ld", length); return false;
    }
    auto *data = static_cast<uint8_t *>(std::malloc(static_cast<size_t>(length)));
    if (!data || std::fread(data, 1, static_cast<size_t>(length), file) != static_cast<size_t>(length)) {
        std::free(data); std::fclose(file); return false;
    }
    std::fclose(file);
    const bool trueType = data[0] == 0x00 && data[1] == 0x01 && data[2] == 0x00 && data[3] == 0x00;
    const bool openType = data[0] == 'O' && data[1] == 'T' && data[2] == 'T' && data[3] == 'O';
    if (!trueType && !openType) { std::free(data); LOGE("font is not a supported sfnt"); return false; }
    gFontData = data; gFontSize = static_cast<size_t>(length);
    LOGI("font ready: %zu bytes", gFontSize); breadcrumb("INFO", "core.font.ready", "%zu bytes", gFontSize);
    return true;
}

bool knownOriginalFont(size_t size) {
    for (size_t expected : kKnownOriginalFontSizes) if (size == expected) return true;
    return false;
}

void hookedFont(void *object, uint32_t a1, uint32_t a2, uint32_t a3) {
    if (object && gFontData && gFontSize && gSwapCount.load(std::memory_order_relaxed) < 3) {
        void *asset = nullptr;
        std::memcpy(&asset, static_cast<uint8_t *>(object) + 0x38, sizeof(asset));
        if (asset) {
            auto *buffer = static_cast<uint8_t *>(asset) + 0x80;
            void *originalData = nullptr; size_t originalSize = 0;
            std::memcpy(&originalData, buffer, sizeof(originalData));
            std::memcpy(&originalSize, buffer + 0x10, sizeof(originalSize));
            if (originalData && originalSize >= 4 && knownOriginalFont(originalSize)) {
                const auto *magic = static_cast<const uint8_t *>(originalData);
                if (magic[0] == 0x00 && magic[1] == 0x01 && magic[2] == 0x00 && magic[3] == 0x00) {
                    std::memcpy(buffer, &gFontData, sizeof(gFontData));
                    std::memcpy(buffer + 0x10, &gFontSize, sizeof(gFontSize));
                    gSwapCount.fetch_add(1, std::memory_order_relaxed);
                    LOGI("font swapped: %zu -> %zu", originalSize, gFontSize);
                    breadcrumb("INFO", "core.font.swap", "old_size=%zu new_size=%zu", originalSize, gFontSize);
                }
            }
        }
    }
    gFontOriginal(object, a1, a2, a3);
}

void *startFontHook(void *) {
    breadcrumb("INFO", "core.unity.wait", "waiting for libunity.so");
    UnitySearch image;
    for (unsigned attempt = 0; attempt < 120 && !image.base; ++attempt) {
        image = findImage(kUnityName);
        if (!image.base) usleep(250000);
    }
    if (!image.base || image.count == 0) {
        LOGW("libunity.so did not load within 30 seconds");
        breadcrumb("WARN", "core.unity.timeout", "libunity.so unavailable after 30 seconds");
        return nullptr;
    }
    uintptr_t target = 0;
    const char *mode = "known";
    if (!validElfAndNote(image)) {
        breadcrumb("WARN", "core.unity.unsupported", "reason=invalid_elf_or_build_id_note");
        return nullptr;
    }
    if (const UnityProfile *profile = matchingProfile(image)) {
        if (!verifiedUnity(image, *profile)) {
            breadcrumb("WARN", "core.unity.unsupported", "reason=known_signature_mismatch profile=%s", profile->name);
            return nullptr;
        }
        target = image.base + profile->entry;
        breadcrumb("INFO", "core.unity.verified", "mode=known profile=%s target=+0x%lx",
                   profile->name, static_cast<unsigned long>(profile->entry));
    } else {
        mode = "dynamic";
        const FontLocation location = locateFontEntry(image);
        if (location.reason) {
            breadcrumb("WARN", "core.unity.unsupported", "reason=%s accessor=%zu caller=%zu candidate=%zu",
                       location.reason, location.accessors, location.callers, location.candidates);
            return nullptr;
        }
        target = location.entry;
        breadcrumb("INFO", "core.unity.dynamic_verified", "accessor=%zu caller=%zu candidate=+0x%lx target=+0x%lx",
                   location.accessors, location.callers, static_cast<unsigned long>(target - image.base),
                   static_cast<unsigned long>(target - image.base));
    }
    if (!loadFont()) return nullptr;
    if (!installHook(reinterpret_cast<void *>(target), reinterpret_cast<void *>(hookedFont),
                     reinterpret_cast<void **>(&gFontOriginal), "Unity font entry")) return nullptr;
    LOGI("verified font hook installed at +0x%lx", static_cast<unsigned long>(target - image.base));
    breadcrumb("INFO", "core.font.hooked", "mode=%s target=+0x%lx", mode, static_cast<unsigned long>(target - image.base));
    return nullptr;
}

void start() {
    if (gStarted.exchange(true)) return;
    breadcrumb("INFO", "core.start", "source core constructor entered");
    pthread_t localize{};
    if (pthread_create(&localize, nullptr, startLocalizeHook, nullptr) == 0) pthread_detach(localize);
    else breadcrumb("ERROR", "core.localize.unsupported", "reason=worker_creation_failed");
    pthread_t thread{};
    if (pthread_create(&thread, nullptr, startFontHook, nullptr) != 0) {
        LOGE("font worker creation failed"); breadcrumb("ERROR", "core.thread.failed", "pthread_create failed"); return;
    }
    pthread_detach(thread);
    LOGI("open-source translation core started"); breadcrumb("INFO", "core.started", "worker created");
}

}  // namespace

__attribute__((constructor)) static void lcpatchInit() { start(); }
