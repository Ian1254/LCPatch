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

namespace {

constexpr char kTag[] = "LCPatchCore";
constexpr char kRuntimeLocalize[] = "/storage/emulated/0/Android/data/com.ProjectMoon.LimbusCompany/cache/Localize/cn";
constexpr char kRuntimeFont[] = "/sdcard/Android/data/com.ProjectMoon.LimbusCompany/cache/Localize/cn/ChineseFont.ttf";
constexpr char kNativeLog[] = "/storage/emulated/0/Android/data/com.ProjectMoon.LimbusCompany/cache/lcpatch-native.log";
constexpr char kUnityName[] = "libunity.so";
constexpr char kIl2CppName[] = "libil2cpp.so";
constexpr uintptr_t kBuildIdNote = 0x308;
constexpr uintptr_t kFontEntry = 0xB851D0;
constexpr uintptr_t kAccessor = 0xB66130;
constexpr size_t kMaxFontBytes = 64U * 1024U * 1024U;
constexpr size_t kMaxSegments = 16;
constexpr size_t kMaxAccessors = 256;
constexpr size_t kMaxCandidates = 32;

constexpr std::array<uint8_t, 24> kExpectedBuildIdNote = {
    0x04, 0x00, 0x00, 0x00, 0x08, 0x00, 0x00, 0x00,
    0x03, 0x00, 0x00, 0x00, 0x47, 0x4e, 0x55, 0x00,
    0x4a, 0x72, 0x4e, 0x4e, 0xe2, 0xe6, 0x92, 0x65,
};
constexpr std::array<uint8_t, 32> kExpectedEntry = {
    0xff, 0x43, 0x02, 0xd1, 0xfe, 0x2b, 0x00, 0xf9,
    0xf8, 0x5f, 0x06, 0xa9, 0xf6, 0x57, 0x07, 0xa9,
    0xf4, 0x4f, 0x08, 0xa9, 0x58, 0x63, 0x00, 0xb0,
    0xf5, 0x03, 0x03, 0x2a, 0xf4, 0x03, 0x02, 0x2a,
};
constexpr std::array<uint8_t, 12> kExpectedAccessor = {
    0x08, 0x1c, 0x40, 0xf9, 0x00, 0x01, 0x02, 0x91,
    0xc0, 0x03, 0x5f, 0xd6,
};
constexpr std::array<size_t, 4> kKnownOriginalFontSizes = {
    0x557F8U, 0xB2B80U, 0x2B173AU, 0x48345EU,
};

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, kTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, kTag, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kTag, __VA_ARGS__)

using FontFn = void (*)(void *, uint32_t, uint32_t, uint32_t);

FontFn gFontOriginal = nullptr;
using OpenFn = int (*)(const char *, int, ...);
using OpenAtFn = int (*)(int, const char *, int, ...);
using FopenFn = FILE *(*)(const char *, const char *);
OpenFn gOpenOriginal = nullptr;
OpenAtFn gOpenAtOriginal = nullptr;
FopenFn gFopenOriginal = nullptr;
uint8_t *gFontData = nullptr;
size_t gFontSize = 0;
std::atomic<unsigned> gSwapCount{0};
std::atomic<unsigned> gRedirectCount{0};
std::atomic<bool> gStarted{false};
pthread_mutex_t gLogLock = PTHREAD_MUTEX_INITIALIZER;

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

bool buildLocalizeRedirect(const char *path, char *output, size_t outputSize) {
    if (!path || !*path || !output || outputSize == 0 ||
        std::strstr(path, kRuntimeLocalize) == path || std::strstr(path, "/../") ||
        std::strstr(path, "/./")) return false;

    const char *marker = std::strstr(path, "/Localize/");
    const char *language = marker ? marker + 10 :
        (std::strncmp(path, "Localize/", 9) == 0 ? path + 9 : nullptr);
    if (!language) return false;
    const char *relative = std::strchr(language, '/');
    if (!relative || !relative[1] || std::strstr(relative + 1, "../")) return false;
    ++relative;

    const int written = std::snprintf(output, outputSize, "%s/%s", kRuntimeLocalize, relative);
    return written > 0 && static_cast<size_t>(written) < outputSize && access(output, R_OK) == 0;
}

const char *redirectReadPath(const char *path, char *buffer, size_t size) {
    if (!buildLocalizeRedirect(path, buffer, size)) return path;
    const unsigned count = gRedirectCount.fetch_add(1, std::memory_order_relaxed);
    if (count < 12) {
        LOGI("localize redirected: %s -> %s", path, buffer);
        breadcrumb("INFO", "core.io.redirect", "%s -> %s", path, buffer);
    }
    return buffer;
}

const char *redirectOpenAtPath(int directory, const char *path, char *redirected,
                               size_t redirectedSize, char *resolved, size_t resolvedSize) {
    if (!path || path[0] == '/') return redirectReadPath(path, redirected, redirectedSize);
    char base[4096]{};
    if (directory == AT_FDCWD) {
        if (!getcwd(base, sizeof(base))) return path;
    } else {
        char descriptor[64]{};
        const int written = std::snprintf(descriptor, sizeof(descriptor), "/proc/self/fd/%d", directory);
        if (written <= 0 || static_cast<size_t>(written) >= sizeof(descriptor)) return path;
        const ssize_t length = readlink(descriptor, base, sizeof(base) - 1);
        if (length <= 0) return path;
        base[length] = '\0';
    }
    const int written = std::snprintf(resolved, resolvedSize, "%s/%s", base, path);
    if (written <= 0 || static_cast<size_t>(written) >= resolvedSize) return path;
    const char *selected = redirectReadPath(resolved, redirected, redirectedSize);
    return selected == resolved ? path : selected;
}

int hookedOpen(const char *path, int flags, ...) {
    mode_t mode = 0;
    const bool needsMode = (flags & O_CREAT) != 0
#ifdef O_TMPFILE
        || (flags & O_TMPFILE) == O_TMPFILE
#endif
        ;
    if (needsMode) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
    }
    char redirected[4096]{};
    const bool readOnly = (flags & (O_WRONLY | O_RDWR | O_CREAT | O_TRUNC)) == 0;
    const char *selected = readOnly ? redirectReadPath(path, redirected, sizeof(redirected)) : path;
    return needsMode ? gOpenOriginal(selected, flags, mode) : gOpenOriginal(selected, flags);
}

int hookedOpenAt(int directory, const char *path, int flags, ...) {
    mode_t mode = 0;
    const bool needsMode = (flags & O_CREAT) != 0
#ifdef O_TMPFILE
        || (flags & O_TMPFILE) == O_TMPFILE
#endif
        ;
    if (needsMode) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
    }
    char redirected[4096]{};
    char resolved[4096]{};
    const bool readOnly = (flags & (O_WRONLY | O_RDWR | O_CREAT | O_TRUNC)) == 0;
    const char *selected = readOnly ? redirectOpenAtPath(directory, path, redirected, sizeof(redirected),
                                                         resolved, sizeof(resolved)) : path;
    return needsMode ? gOpenAtOriginal(directory, selected, flags, mode)
                     : gOpenAtOriginal(directory, selected, flags);
}

FILE *hookedFopen(const char *path, const char *mode) {
    char redirected[4096];
    const bool readOnly = mode && mode[0] == 'r' && std::strchr(mode, '+') == nullptr;
    return gFopenOriginal(readOnly ? redirectReadPath(path, redirected, sizeof(redirected)) : path, mode);
}

bool installHook(void *target, void *replacement, void **original, const char *name) {
    if (!target) {
        LOGE("symbol missing: %s", name);
        return false;
    }
    const int result = DobbyHook(target, replacement, original);
    if (result != 0 || !*original) {
        LOGE("hook failed: %s (%d)", name, result);
        return false;
    }
    return true;
}

struct Segment {
    uintptr_t begin;
    uintptr_t end;
    int protection;
};

struct UnitySearch {
    uintptr_t base = 0;
    Segment readable[kMaxSegments]{};
    size_t readableCount = 0;
    Segment executable[kMaxSegments]{};
    size_t count = 0;
};

struct ImageRequest {
    const char *suffix;
    UnitySearch result;
};

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
        if (result->readableCount < kMaxSegments) {
            result->readable[result->readableCount++] = {
                result->base + header.p_vaddr,
                result->base + header.p_vaddr + header.p_memsz,
                protection,
            };
        }
        if ((header.p_flags & PF_X) && result->count < kMaxSegments) {
            result->executable[result->count++] = {
                result->base + header.p_vaddr,
                result->base + header.p_vaddr + header.p_memsz,
                protection,
            };
        }
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

int protectionAt(const UnitySearch &image, uintptr_t address) {
    for (size_t i = 0; i < image.readableCount; ++i) {
        if (address >= image.readable[i].begin && address < image.readable[i].end) {
            return image.readable[i].protection;
        }
    }
    return 0;
}

bool patchFileImports(const UnitySearch &image, const char *moduleName) {
    breadcrumb("INFO", "core.io.begin", "scanning %s file PLT", moduleName);
    const Elf64_Dyn *dynamic = nullptr;
    for (size_t i = 0; i < image.readableCount && !dynamic; ++i) {
        const Segment &segment = image.readable[i];
        const auto *elf = reinterpret_cast<const Elf64_Ehdr *>(image.base);
        const auto *headers = reinterpret_cast<const Elf64_Phdr *>(image.base + elf->e_phoff);
        for (Elf64_Half n = 0; n < elf->e_phnum; ++n) {
            if (headers[n].p_type == PT_DYNAMIC) {
                const uintptr_t address = image.base + headers[n].p_vaddr;
                if (address >= segment.begin && address + sizeof(Elf64_Dyn) <= segment.end) {
                    dynamic = reinterpret_cast<const Elf64_Dyn *>(address);
                    break;
                }
            }
        }
    }
    if (!dynamic) {
        breadcrumb("ERROR", "core.io.result", "%s dynamic section unavailable", moduleName);
        return false;
    }

    Elf64_Addr stringValue = 0;
    Elf64_Addr symbolValue = 0;
    Elf64_Addr jumpValue = 0;
    size_t stringBytes = 0;
    size_t jumpBytes = 0;
    Elf64_Sxword pltType = 0;
    for (const Elf64_Dyn *entry = dynamic; entry->d_tag != DT_NULL; ++entry) {
        switch (entry->d_tag) {
            case DT_STRTAB: stringValue = entry->d_un.d_ptr; break;
            case DT_STRSZ: stringBytes = entry->d_un.d_val; break;
            case DT_SYMTAB: symbolValue = entry->d_un.d_ptr; break;
            case DT_JMPREL: jumpValue = entry->d_un.d_ptr; break;
            case DT_PLTRELSZ: jumpBytes = entry->d_un.d_val; break;
            case DT_PLTREL: pltType = entry->d_un.d_val; break;
            default: break;
        }
    }
    const uintptr_t stringsAt = loadedAddress(image, stringValue, 1);
    const uintptr_t symbolsAt = loadedAddress(image, symbolValue, sizeof(Elf64_Sym));
    const uintptr_t jumpsAt = loadedAddress(image, jumpValue, jumpBytes);
    if (!stringsAt || !symbolsAt || !jumpsAt || !jumpBytes || !stringBytes || pltType != DT_RELA) {
        breadcrumb("ERROR", "core.io.result", "%s has unsupported PLT metadata", moduleName);
        return false;
    }

    const auto *strings = reinterpret_cast<const char *>(stringsAt);
    const auto *symbols = reinterpret_cast<const Elf64_Sym *>(symbolsAt);
    const auto *relocations = reinterpret_cast<const Elf64_Rela *>(jumpsAt);
    const size_t relocationCount = jumpBytes / sizeof(Elf64_Rela);
    size_t fopenPatched = 0;
    size_t openPatched = 0;
    size_t openAtPatched = 0;
    const long pageSizeValue = sysconf(_SC_PAGESIZE);
    if (pageSizeValue <= 0) return false;
    const uintptr_t pageSize = static_cast<uintptr_t>(pageSizeValue);
    for (size_t i = 0; i < relocationCount; ++i) {
        const size_t symbolIndex = ELF64_R_SYM(relocations[i].r_info);
        const uintptr_t symbolAddress = symbolsAt + symbolIndex * sizeof(Elf64_Sym);
        if (!rangeContains(image.readable, image.readableCount, symbolAddress, sizeof(Elf64_Sym))) continue;
        if (symbols[symbolIndex].st_name >= stringBytes) continue;
        const char *name = strings + symbols[symbolIndex].st_name;
        void *replacement = nullptr;
        void **originalStorage = nullptr;
        size_t *counter = nullptr;
        if (std::strcmp(name, "fopen") == 0 || std::strcmp(name, "fopen64") == 0) {
            replacement = reinterpret_cast<void *>(hookedFopen);
            originalStorage = reinterpret_cast<void **>(&gFopenOriginal);
            counter = &fopenPatched;
        } else if (std::strcmp(name, "open") == 0 || std::strcmp(name, "open64") == 0 ||
                   std::strcmp(name, "__open_2") == 0) {
            replacement = reinterpret_cast<void *>(hookedOpen);
            originalStorage = reinterpret_cast<void **>(&gOpenOriginal);
            counter = &openPatched;
        } else if (std::strcmp(name, "openat") == 0 || std::strcmp(name, "openat64") == 0 ||
                   std::strcmp(name, "__openat_2") == 0) {
            replacement = reinterpret_cast<void *>(hookedOpenAt);
            originalStorage = reinterpret_cast<void **>(&gOpenAtOriginal);
            counter = &openAtPatched;
        } else {
            continue;
        }
        const uintptr_t slotAddress = loadedAddress(image, relocations[i].r_offset, sizeof(void *));
        if (!slotAddress) continue;
        auto **slot = reinterpret_cast<void **>(slotAddress);
        void *original = __atomic_load_n(slot, __ATOMIC_ACQUIRE);
        if (!original || original == replacement) continue;
        const uintptr_t page = slotAddress & ~(pageSize - 1U);
        const int originalProtection = protectionAt(image, slotAddress);
        if (!originalProtection || mprotect(reinterpret_cast<void *>(page), pageSize,
                                            originalProtection | PROT_WRITE) != 0) continue;
        if (!*originalStorage) *originalStorage = original;
        __atomic_store_n(slot, replacement, __ATOMIC_RELEASE);
        mprotect(reinterpret_cast<void *>(page), pageSize, originalProtection);
        ++*counter;
    }
    const size_t total = fopenPatched + openPatched + openAtPatched;
    breadcrumb(total ? "INFO" : "WARN", "core.io.result",
               "module=%s mode=plt fopen=%zu open=%zu openat=%zu",
               moduleName, fopenPatched, openPatched, openAtPatched);
    return total > 0;
}

bool verifiedUnity(const UnitySearch &image) {
    if (!image.base ||
        !rangeContains(image.readable, image.readableCount, image.base, 6) ||
        !rangeContains(image.readable, image.readableCount, image.base + kBuildIdNote,
                       kExpectedBuildIdNote.size()) ||
        !rangeContains(image.executable, image.count, image.base + kFontEntry, kExpectedEntry.size()) ||
        !rangeContains(image.executable, image.count, image.base + kAccessor, kExpectedAccessor.size())) return false;
    const auto *elf = reinterpret_cast<const uint8_t *>(image.base);
    if (elf[0] != 0x7f || elf[1] != 'E' || elf[2] != 'L' || elf[3] != 'F' ||
        elf[4] != 2 || elf[5] != 1) return false;
    return memoryMatches(image.base, kBuildIdNote, kExpectedBuildIdNote) &&
           memoryMatches(image.base, kFontEntry, kExpectedEntry) &&
           memoryMatches(image.base, kAccessor, kExpectedAccessor);
}

bool containsAddress(const uintptr_t *values, size_t count, uintptr_t value) {
    for (size_t i = 0; i < count; ++i) {
        if (values[i] == value) return true;
    }
    return false;
}

bool isStackFrameStart(uint32_t instruction) {
    return (instruction & 0xffc003ffU) == 0xd10003ffU;
}

bool savesLinkRegister(uint32_t instruction) {
    return (instruction & 0xffc003ffU) == 0xf90003feU ||
           (instruction & 0xffc07fffU) == 0xa9007bfdU;
}

bool isReturn(uint32_t instruction) {
    return instruction == 0xd65f03c0U;
}

uintptr_t branchTarget(uintptr_t pc, uint32_t instruction) {
    const int64_t delta = static_cast<int64_t>(static_cast<int32_t>(instruction << 6)) >> 4;
    return static_cast<uintptr_t>(static_cast<int64_t>(pc) + delta);
}

bool validatesCaller(uintptr_t call, uintptr_t segmentEnd) {
    if (call + 15 * sizeof(uint32_t) > segmentEnd) return false;
    const auto *after = reinterpret_cast<const uint32_t *>(call + sizeof(uint32_t));
    bool length = false;
    bool data = false;
    bool nullCheck = false;
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
        for (size_t n = i + 1; n < limit; ++n) {
            if (savesLinkRegister(words[n])) return segment.begin + i * sizeof(uint32_t);
        }
    }
    return 0;
}

uintptr_t locateFontEntry(const UnitySearch &image, size_t *accessorCount, size_t *candidateCount) {
    uintptr_t accessors[kMaxAccessors]{};
    uintptr_t candidates[kMaxCandidates]{};
    size_t storedAccessors = 0;
    *accessorCount = 0;
    *candidateCount = 0;
    for (size_t s = 0; s < image.count; ++s) {
        const Segment &segment = image.executable[s];
        for (uintptr_t at = segment.begin; at + kExpectedAccessor.size() <= segment.end; at += 4) {
            if (std::memcmp(reinterpret_cast<const void *>(at), kExpectedAccessor.data(),
                            kExpectedAccessor.size()) == 0) {
                ++*accessorCount;
                if (storedAccessors == kMaxAccessors) return 0;
                accessors[storedAccessors++] = at;
            }
        }
    }
    for (size_t s = 0; s < image.count; ++s) {
        const Segment &segment = image.executable[s];
        for (uintptr_t at = segment.begin; at + sizeof(uint32_t) <= segment.end; at += 4) {
            const uint32_t instruction = *reinterpret_cast<const uint32_t *>(at);
            if ((instruction & 0xfc000000U) != 0x94000000U ||
                !containsAddress(accessors, storedAccessors, branchTarget(at, instruction)) ||
                !validatesCaller(at, segment.end)) continue;
            const uintptr_t start = findFunctionStart(segment, at);
            if (!start || containsAddress(candidates, *candidateCount, start)) continue;
            if (*candidateCount == kMaxCandidates) return 0;
            candidates[(*candidateCount)++] = start;
        }
    }
    return *candidateCount == 1 ? candidates[0] : 0;
}

bool loadFont() {
    FILE *file = std::fopen(kRuntimeFont, "rb");
    if (!file) {
        LOGE("font missing: %s", kRuntimeFont);
        breadcrumb("ERROR", "core.font.missing", "%s", kRuntimeFont);
        return false;
    }
    if (std::fseek(file, 0, SEEK_END) != 0) {
        std::fclose(file);
        return false;
    }
    const long length = std::ftell(file);
    if (length < 12 || static_cast<unsigned long>(length) > kMaxFontBytes ||
        std::fseek(file, 0, SEEK_SET) != 0) {
        std::fclose(file);
        LOGE("invalid font size: %ld", length);
        return false;
    }
    auto *data = static_cast<uint8_t *>(std::malloc(static_cast<size_t>(length)));
    if (!data || std::fread(data, 1, static_cast<size_t>(length), file) != static_cast<size_t>(length)) {
        std::free(data);
        std::fclose(file);
        return false;
    }
    std::fclose(file);
    const bool trueType = data[0] == 0x00 && data[1] == 0x01 && data[2] == 0x00 && data[3] == 0x00;
    const bool openType = data[0] == 'O' && data[1] == 'T' && data[2] == 'T' && data[3] == 'O';
    if (!trueType && !openType) {
        std::free(data);
        LOGE("font is not a supported sfnt");
        return false;
    }
    gFontData = data;
    gFontSize = static_cast<size_t>(length);
    LOGI("font ready: %zu bytes", gFontSize);
    breadcrumb("INFO", "core.font.ready", "%zu bytes", gFontSize);
    return true;
}

bool knownOriginalFont(size_t size) {
    for (size_t expected : kKnownOriginalFontSizes) {
        if (size == expected) return true;
    }
    return false;
}

void hookedFont(void *object, uint32_t a1, uint32_t a2, uint32_t a3) {
    if (object && gFontData && gFontSize && gSwapCount.load(std::memory_order_relaxed) < 3) {
        void *asset = nullptr;
        std::memcpy(&asset, static_cast<uint8_t *>(object) + 0x38, sizeof(asset));
        if (asset) {
            auto *buffer = static_cast<uint8_t *>(asset) + 0x80;
            void *originalData = nullptr;
            size_t originalSize = 0;
            std::memcpy(&originalData, buffer, sizeof(originalData));
            std::memcpy(&originalSize, buffer + 0x10, sizeof(originalSize));
            if (originalData && originalSize >= 4 && knownOriginalFont(originalSize)) {
                const auto *magic = static_cast<const uint8_t *>(originalData);
                if (magic[0] == 0x00 && magic[1] == 0x01 && magic[2] == 0x00 && magic[3] == 0x00) {
                    std::memcpy(buffer, &gFontData, sizeof(gFontData));
                    std::memcpy(buffer + 0x10, &gFontSize, sizeof(gFontSize));
                    gSwapCount.fetch_add(1, std::memory_order_relaxed);
                    LOGI("font swapped: %zu -> %zu", originalSize, gFontSize);
                    breadcrumb("INFO", "core.font.swap", "%zu -> %zu", originalSize, gFontSize);
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
    UnitySearch il2cpp;
    for (unsigned attempt = 0; attempt < 40 && !il2cpp.base; ++attempt) {
        il2cpp = findImage(kIl2CppName);
        if (!il2cpp.base) usleep(50000);
    }
    if (il2cpp.base) patchFileImports(il2cpp, kIl2CppName);
    else breadcrumb("WARN", "core.io.result", "%s not loaded; imports not patched", kIl2CppName);
    patchFileImports(image, kUnityName);
    uintptr_t target = 0;
    if (verifiedUnity(image)) {
        target = image.base + kFontEntry;
        LOGI("known Unity build verified");
        breadcrumb("INFO", "core.unity.verified", "known build; target=+0x%lx",
                   static_cast<unsigned long>(kFontEntry));
    } else {
        size_t accessorCount = 0;
        size_t candidateCount = 0;
        const uintptr_t candidate = locateFontEntry(image, &accessorCount, &candidateCount);
        LOGW("unapproved Unity build: accessor=%zu caller=%zu candidate=+0x%lx; font hook skipped",
             accessorCount, candidateCount,
             static_cast<unsigned long>(candidate ? candidate - image.base : 0));
        breadcrumb("WARN", "core.unity.unsupported", "accessor=%zu caller=%zu candidate=+0x%lx",
                   accessorCount, candidateCount,
                   static_cast<unsigned long>(candidate ? candidate - image.base : 0));
        return nullptr;
    }
    if (!loadFont()) return nullptr;
    if (!installHook(reinterpret_cast<void *>(target), reinterpret_cast<void *>(hookedFont),
                     reinterpret_cast<void **>(&gFontOriginal), "Unity font entry")) return nullptr;
    LOGI("verified font hook installed at +0x%lx",
         static_cast<unsigned long>(target - image.base));
    breadcrumb("INFO", "core.font.hooked", "target=+0x%lx",
               static_cast<unsigned long>(target - image.base));
    return nullptr;
}

void start() {
    if (gStarted.exchange(true)) return;
    breadcrumb("INFO", "core.start", "source core constructor entered");
    pthread_t thread{};
    if (pthread_create(&thread, nullptr, startFontHook, nullptr) != 0) {
        LOGE("font worker creation failed");
        breadcrumb("ERROR", "core.thread.failed", "pthread_create failed");
        return;
    }
    pthread_detach(thread);
    LOGI("open-source translation core started");
    breadcrumb("INFO", "core.started", "worker created");
}

}  // namespace

__attribute__((constructor)) static void lcpatchInit() {
    start();
}
