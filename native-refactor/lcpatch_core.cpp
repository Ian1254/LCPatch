#include <android/log.h>
#include <dlfcn.h>
#include <elf.h>
#include <fcntl.h>
#include <link.h>
#include <pthread.h>
#include <sched.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <time.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#include <string>
#include <unordered_map>
#include "text_asset_paths.h"
#include "dobby.h"
#include "verification_read_guard.h"
#include "verification_signature_scan.h"

namespace {

constexpr char kTag[] = "LCPatchCore";
constexpr char kRuntimeLocalize[] = "/storage/emulated/0/Android/data/com.ProjectMoon.LimbusCompany/cache/Localize/cn";
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
lcpatch::VerificationRange gVerificationRange{};
lcpatch::VerificationRange gVerificationImage{};
std::atomic<bool> gVerificationProfile{false};
std::atomic<bool> gVerificationReady{false};
bool gVerificationMasked = false; // Published before gVerificationProfile.store(release).
lcpatch::SignatureScanRange gVerificationCodeRanges[kMaxSegments]{};
size_t gVerificationCodeRangeCount = 0;
std::atomic<unsigned> gVerificationReads{0};
struct VerificationProfile {
    const char *name;
    std::array<uint8_t, 20> buildId;
    uintptr_t begin;
    uintptr_t end;
    std::array<uint8_t, 32> entry;
};
const VerificationProfile *gVerificationSelected = nullptr;
// Match complete prologue plus ADRP opcode/rd and LDRB opcode/registers.
// Mask only relocatable page immediates and the validated LDRB field offset.
constexpr std::array<uint8_t, 32> kVerificationEntryMask = {
    0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
    0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
    0x1f, 0x00, 0x00, 0x9f, 0x1f, 0x00, 0x00, 0x9f,
    0xff, 0xff, 0xff, 0xff, 0xff, 0x03, 0xc0, 0xff,
};
constexpr size_t kVerificationFileCallOffset = 0x88;
constexpr VerificationProfile kVerificationProfiles[] = {
    {"limbus-1.117.1", {
    0xbe, 0x4d, 0xb3, 0x5a, 0xd4, 0xd0, 0x6a, 0x2c, 0x2b, 0x9f,
    0x2a, 0x79, 0xb4, 0x1b, 0x29, 0xdf, 0x0c, 0x34, 0xd2, 0xd2,
    }, 0x52e2a58, 0x52e2c44, {
    0xff, 0x43, 0x01, 0xd1, 0xfe, 0x5f, 0x02, 0xa9,
    0xf6, 0x57, 0x03, 0xa9, 0xf4, 0x4f, 0x04, 0xa9,
    0xb7, 0xcd, 0x02, 0x90, 0x16, 0x95, 0x02, 0xf0,
    0xf4, 0x03, 0x02, 0x2a, 0xe8, 0x4a, 0x71, 0x39,
    }},
    {"limbus-1.118.0", {
        0xb3, 0x04, 0x46, 0x4d, 0xbb, 0x7a, 0x4a, 0x55, 0x41, 0x0a,
        0x0f, 0x52, 0xec, 0xcc, 0x55, 0x8a, 0x9b, 0x1b, 0xb7, 0x79,
    }, 0x5326908, 0x5326af4, {
        0xff, 0x43, 0x01, 0xd1, 0xfe, 0x5f, 0x02, 0xa9,
        0xf6, 0x57, 0x03, 0xa9, 0xf4, 0x4f, 0x04, 0xa9,
        0x57, 0xcd, 0x02, 0xd0, 0xb6, 0x94, 0x02, 0xd0,
        0xf4, 0x03, 0x02, 0x2a, 0xe8, 0x7a, 0x71, 0x39,
    }},
};

void breadcrumb(const char *level, const char *code, const char *format, ...) {
    char message[1024]{};
    va_list args;
    va_start(args, format);
    std::vsnprintf(message, sizeof(message), format, args);
    va_end(args);
    const int priority = std::strcmp(level, "ERROR") == 0 ? ANDROID_LOG_ERROR :
        std::strcmp(level, "WARN") == 0 ? ANDROID_LOG_WARN : ANDROID_LOG_INFO;
    // The external file may not exist yet. Always keep a logcat copy too.
    __android_log_print(priority, kTag, "%s pid=%d tid=%ld %s", code, getpid(), syscall(SYS_gettid), message);
    timespec now{};
    clock_gettime(CLOCK_REALTIME, &now);
    char line[1280]{};
    const int length = std::snprintf(line, sizeof(line), "%lld|%s|%s|pid=%d tid=%ld %s\n",
        static_cast<long long>(now.tv_sec) * 1000LL + now.tv_nsec / 1000000LL,
        level, code, getpid(), syscall(SYS_gettid), message);
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
bool verificationEntryReady();

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
    // Unknown builds must not inherit beta.3's low-level redirection: the
    // same path is opened by both the game verifier and the text loader.
    const bool profileMatched = gVerificationProfile.load(std::memory_order_acquire);
    if (!profileMatched) return path;
    // Revalidate using the SAME exact/masked policy as initial discovery.
    // A masked discovery must never be followed by an exact-byte comparison.
    if (!gVerificationReady.load(std::memory_order_acquire) && verificationEntryReady()) {
        if (!gVerificationReady.exchange(true, std::memory_order_acq_rel))
            breadcrumb("INFO", "core.io.verify_guard.ready", "reference=%s mode=%s; read-only stack guard",
                       gVerificationSelected->name, gVerificationMasked ? "masked_signature" : "build_id");
    }
    const bool signatureReady = gVerificationReady.load(std::memory_order_acquire);
    if (!lcpatch::shouldRedirectLocalizationRead(profileMatched, signatureReady, false))
        return path;  // Keep official bytes while the verification guard is unavailable.
    if (lcpatch::isResourceVerificationRead(gVerificationRange, gVerificationImage)) {
        const unsigned count = gVerificationReads.fetch_add(1, std::memory_order_relaxed) + 1;
        if (count <= 6 || count % 128 == 0)
            breadcrumb("INFO", "core.io.verify_read", "original retained count=%u path=%s", count, path);
        return path;
    }
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
        va_list args; va_start(args, flags);
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
        va_list args; va_start(args, flags);
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
    breadcrumb("INFO", "core.hook.begin", "name=%s target=%p", name, target);
    if (!target) { LOGE("symbol missing: %s", name); return false; }
    const int result = DobbyHook(target, replacement, original);
    if (result != 0 || !*original) { LOGE("hook failed: %s (%d)", name, result); return false; }
    breadcrumb("INFO", "core.hook.ready", "name=%s", name);
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

bool readSelfCode(uintptr_t address, uint8_t *destination, size_t bytes) {
    iovec local{destination, bytes};
    iovec remote{reinterpret_cast<void *>(address), bytes};
    // A failed read (e.g. code is protected or not yet available) must never
    // fault the game process or turn into an unverified positive match.
    return syscall(SYS_process_vm_readv, getpid(), &local, 1, &remote, 1, 0) ==
           static_cast<ssize_t>(bytes);
}

bool verificationEntryReady() {
    if (!gVerificationSelected || !gVerificationRange.begin ||
        gVerificationRange.end <= gVerificationRange.begin) return false;
    const auto &profile = *gVerificationSelected;
    // Preserve beta.4's proven exact check for recognized Build IDs. Their
    // verified executable mapping is already used by the original code path;
    // introducing a syscall dependency here would regress known-good builds.
    if (!gVerificationMasked)
        return std::memcmp(reinterpret_cast<const void *>(gVerificationRange.begin),
                           profile.entry.data(), profile.entry.size()) == 0;

    constexpr size_t kMaxReadSpan = 512;
    uint8_t bytes[kMaxReadSpan]{};
    const size_t methodSize = profile.end - profile.begin;
    if (!methodSize || methodSize > sizeof(bytes) ||
        !readSelfCode(gVerificationRange.begin, bytes, methodSize)) return false;
    const lcpatch::VerificationSignature signature{
        profile.entry.data(), profile.entry.size(),
        methodSize, kVerificationFileCallOffset,
        kVerificationEntryMask.data(), true,
    };
    // The masked path must pass the same entry, call-target AND body-shape
    // checks used during discovery. Never re-enable from a weaker check.
    return lcpatch::validatesVerificationCandidate(
        gVerificationCodeRanges, gVerificationCodeRangeCount,
        gVerificationRange.begin, signature, bytes, methodSize);
}

// Diagnostic only: capture the first two ambiguous verifier candidates from
// *running* decrypted code. Disk IL2CPP sections may be protected/opaque.
// This never chooses a candidate, installs new hooks, or changes redirection.
void logAmbiguousVerificationCandidates(
    const UnitySearch &image, const lcpatch::VerificationSignatureMatch &match) {
    if (match.candidateCount < 2) return;
    constexpr size_t kSampleLength = 0x1ec;
    constexpr size_t kLineBytes = 64;
    for (size_t index = 0; index < 2; ++index) {
        const uintptr_t at = match.diagnosticCandidates[index];
        if (!at || at < image.base ||
            !rangeContains(image.executable, image.count, at, kSampleLength)) {
            breadcrumb("WARN", "core.io.verify_guard.candidate", "index=%zu invalid range", index);
            continue;
        }
        uint8_t sample[kSampleLength]{};
        if (!readSelfCode(at, sample, sizeof(sample))) {
            breadcrumb("WARN", "core.io.verify_guard.candidate", "index=%zu unavailable", index);
            continue;
        }
        uint32_t bl = 0;
        std::memcpy(&bl, sample + kVerificationFileCallOffset, sizeof(bl));
        const int64_t displacement =
            static_cast<int64_t>(static_cast<int32_t>(bl << 6)) >> 4;
        const uintptr_t callSite = at + kVerificationFileCallOffset;
        const uintptr_t target = static_cast<uintptr_t>(
            static_cast<int64_t>(callSite) + displacement);
        breadcrumb("INFO", "core.io.verify_guard.candidate",
                   "index=%zu rva=+0x%lx method_bytes=%zu bl=0x%08x call_rva=+0x%lx",
                   index, static_cast<unsigned long>(at - image.base),
                   sizeof(sample), bl, static_cast<unsigned long>(target - image.base));
        for (size_t offset = 0; offset < sizeof(sample); offset += kLineBytes) {
            char hex[2 * kLineBytes + 1]{};
            const size_t length = std::min(kLineBytes, sizeof(sample) - offset);
            for (size_t n = 0; n < length; ++n) {
                static constexpr char kHex[] = "0123456789abcdef";
                hex[2 * n] = kHex[sample[offset + n] >> 4];
                hex[2 * n + 1] = kHex[sample[offset + n] & 0xf];
            }
            breadcrumb("INFO", "core.io.verify_guard.candidate_bytes",
                       "index=%zu offset=+0x%zx hex=%s", index, offset, hex);
        }
    }
}

void configureVerificationGuard(const UnitySearch &image) {
    if (!image.base || image.count == 0 ||
        !rangeContains(image.readable, image.readableCount, image.base, sizeof(Elf64_Ehdr))) {
        breadcrumb("WARN", "core.io.verify_guard.unsupported", "invalid ELF or executable segments");
        return;
    }
    const auto *elf = reinterpret_cast<const Elf64_Ehdr *>(image.base);
    if (std::memcmp(elf->e_ident, ELFMAG, SELFMAG) != 0 ||
        elf->e_ident[EI_CLASS] != ELFCLASS64 ||
        elf->e_phentsize != sizeof(Elf64_Phdr) || !elf->e_phnum ||
        elf->e_phnum > 256 || elf->e_phoff > UINTPTR_MAX - image.base ||
        !rangeContains(image.readable, image.readableCount,
                       image.base + elf->e_phoff,
                       static_cast<size_t>(elf->e_phnum) * sizeof(Elf64_Phdr))) {
        breadcrumb("WARN", "core.io.verify_guard.unsupported", "ELF program headers invalid");
        return;
    }
    const auto *headers = reinterpret_cast<const Elf64_Phdr *>(image.base + elf->e_phoff);
    const VerificationProfile *identified = nullptr;
    for (Elf64_Half i = 0; i < elf->e_phnum && !identified; ++i) {
        if (headers[i].p_type != PT_NOTE) continue;
        const uintptr_t begin = image.base + headers[i].p_vaddr;
        if (!rangeContains(image.readable, image.readableCount, begin, headers[i].p_memsz)) continue;
        size_t offset = 0;
        while (offset + sizeof(Elf64_Nhdr) <= headers[i].p_memsz) {
            const auto *note = reinterpret_cast<const Elf64_Nhdr *>(begin + offset);
            const size_t names = (static_cast<size_t>(note->n_namesz) + 3) & ~size_t(3);
            const size_t data = (static_cast<size_t>(note->n_descsz) + 3) & ~size_t(3);
            const size_t remaining = headers[i].p_memsz - offset - sizeof(Elf64_Nhdr);
            if (names > remaining || data > remaining - names) break;
            const auto *name = reinterpret_cast<const uint8_t *>(note + 1);
            if (note->n_type == NT_GNU_BUILD_ID && note->n_namesz == 4 &&
                note->n_descsz == 20 && std::memcmp(name, "GNU", 4) == 0) {
                // Confirm the running ELF matches the uploaded split APK.
                char buildIdHex[41]{};
                static constexpr char kHex[] = "0123456789abcdef";
                for (size_t n = 0; n < 20; ++n) {
                    const uint8_t byte = name[names + n];
                    buildIdHex[2 * n] = kHex[byte >> 4];
                    buildIdHex[2 * n + 1] = kHex[byte & 0xf];
                }
                breadcrumb("INFO", "core.io.verify_guard.build_id", "%s", buildIdHex);
                for (const auto &profile : kVerificationProfiles) {
                    if (std::memcmp(name + names, profile.buildId.data(), profile.buildId.size()) == 0) {
                        identified = &profile;
                        break;
                    }
                }
            }
            offset += sizeof(Elf64_Nhdr) + names + data;
        }
    }

    uintptr_t begin = identified ? image.base + identified->begin : 0;
    uintptr_t end = identified ? image.base + identified->end : 0;
    const char *mode = "build_id";
    if (!identified) {
        // The two analyzed versions share one masked 8-instruction prologue.
        // New Build IDs may reuse it after ADRP/field-offset relocation; the
        // BL caller must still resolve to executable code outside this method.
        // A unique candidate is required. We do not guess when code changes.
        lcpatch::SignatureScanRange ranges[kMaxSegments]{};
        for (size_t i = 0; i < image.count; ++i)
            ranges[i] = {image.executable[i].begin, image.executable[i].end};
        const auto &reference = kVerificationProfiles[0];
        const lcpatch::VerificationSignature signature{
            reference.entry.data(), reference.entry.size(),
            reference.end - reference.begin,
            kVerificationFileCallOffset, kVerificationEntryMask.data(),
            true, // 1.118.1: reject early-RET short-function collisions.
        };
        const auto match = lcpatch::findUniqueVerificationSignature(
            ranges, image.count, &signature, 1, readSelfCode);
        if (match.scanComplete && match.candidateCount == 1) {
            identified = &reference;
            begin = match.address;
            end = begin + identified->end - identified->begin;
            mode = "masked_signature";
        } else {
            breadcrumb("WARN", "core.io.verify_guard.discovery",
                       "masked signature scan=%s candidates=%u; official file reads preserved",
                       match.scanComplete ? "complete" : "incomplete", match.candidateCount);
            logAmbiguousVerificationCandidates(image, match);
        }
    }

    if (identified && end > begin &&
        rangeContains(image.executable, image.count, begin, end - begin)) {
        gVerificationRange = {begin, end};
        gVerificationSelected = identified;
        gVerificationImage = {image.executable[0].begin, image.executable[0].end};
        for (size_t i = 1; i < image.count; ++i) {
            if (image.executable[i].begin < gVerificationImage.begin)
                gVerificationImage.begin = image.executable[i].begin;
            if (image.executable[i].end > gVerificationImage.end)
                gVerificationImage.end = image.executable[i].end;
        }
        gVerificationMasked = std::strcmp(mode, "masked_signature") == 0;
        if (gVerificationMasked) {
            gVerificationCodeRangeCount = image.count;
            for (size_t i = 0; i < image.count; ++i)
                gVerificationCodeRanges[i] = {image.executable[i].begin, image.executable[i].end};
        }
        gVerificationProfile.store(true, std::memory_order_release);
        breadcrumb("INFO", "core.io.verify_guard.profile",
                   "mode=%s reference=%s target=+0x%lx awaiting exact entry signature",
                   mode, identified->name, static_cast<unsigned long>(begin - image.base));
    } else {
        breadcrumb("WARN", "core.io.verify_guard.unsupported",
                   "no verified matching method; text redirect disabled, official reads preserved");
    }
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
        if (address >= image.readable[i].begin && address < image.readable[i].end) return image.readable[i].protection;
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
                    dynamic = reinterpret_cast<const Elf64_Dyn *>(address); break;
                }
            }
        }
    }
    if (!dynamic) { breadcrumb("ERROR", "core.io.result", "%s dynamic section unavailable", moduleName); return false; }
    Elf64_Addr stringValue = 0, symbolValue = 0, jumpValue = 0;
    size_t stringBytes = 0, jumpBytes = 0;
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
        breadcrumb("ERROR", "core.io.result", "%s has unsupported PLT metadata", moduleName); return false;
    }
    const auto *strings = reinterpret_cast<const char *>(stringsAt);
    const auto *symbols = reinterpret_cast<const Elf64_Sym *>(symbolsAt);
    const auto *relocations = reinterpret_cast<const Elf64_Rela *>(jumpsAt);
    const size_t relocationCount = jumpBytes / sizeof(Elf64_Rela);
    size_t fopenPatched = 0, openPatched = 0, openAtPatched = 0;
    const long pageSizeValue = sysconf(_SC_PAGESIZE);
    if (pageSizeValue <= 0) return false;
    const uintptr_t pageSize = static_cast<uintptr_t>(pageSizeValue);
    for (size_t i = 0; i < relocationCount; ++i) {
        const size_t symbolIndex = ELF64_R_SYM(relocations[i].r_info);
        const uintptr_t symbolAddress = symbolsAt + symbolIndex * sizeof(Elf64_Sym);
        if (!rangeContains(image.readable, image.readableCount, symbolAddress, sizeof(Elf64_Sym))) continue;
        if (symbols[symbolIndex].st_name >= stringBytes) continue;
        const char *name = strings + symbols[symbolIndex].st_name;
        void *replacement = nullptr; void **originalStorage = nullptr; size_t *counter = nullptr;
        if (std::strcmp(name, "fopen") == 0 || std::strcmp(name, "fopen64") == 0) {
            replacement = reinterpret_cast<void *>(hookedFopen); originalStorage = reinterpret_cast<void **>(&gFopenOriginal); counter = &fopenPatched;
        } else if (std::strcmp(name, "open") == 0 || std::strcmp(name, "open64") == 0 || std::strcmp(name, "__open_2") == 0) {
            replacement = reinterpret_cast<void *>(hookedOpen); originalStorage = reinterpret_cast<void **>(&gOpenOriginal); counter = &openPatched;
        } else if (std::strcmp(name, "openat") == 0 || std::strcmp(name, "openat64") == 0 || std::strcmp(name, "__openat_2") == 0) {
            replacement = reinterpret_cast<void *>(hookedOpenAt); originalStorage = reinterpret_cast<void **>(&gOpenAtOriginal); counter = &openAtPatched;
        } else continue;
        const uintptr_t slotAddress = loadedAddress(image, relocations[i].r_offset, sizeof(void *));
        if (!slotAddress) continue;
        auto **slot = reinterpret_cast<void **>(slotAddress);
        void *original = __atomic_load_n(slot, __ATOMIC_ACQUIRE);
        if (!original || original == replacement) continue;
        const uintptr_t page = slotAddress & ~(pageSize - 1U);
        const int originalProtection = protectionAt(image, slotAddress);
        if (!originalProtection || mprotect(reinterpret_cast<void *>(page), pageSize, originalProtection | PROT_WRITE) != 0) continue;
        if (!*originalStorage) *originalStorage = original;
        __atomic_store_n(slot, replacement, __ATOMIC_RELEASE);
        mprotect(reinterpret_cast<void *>(page), pageSize, originalProtection);
        ++*counter;
    }
    const size_t total = fopenPatched + openPatched + openAtPatched;
    breadcrumb(total ? "INFO" : "WARN", "core.io.result", "module=%s mode=plt fopen=%zu open=%zu openat=%zu",
               moduleName, fopenPatched, openPatched, openAtPatched);
    return total > 0;
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


#include "text_asset_hook.inc"

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
    if (il2cpp.base) {
        configureVerificationGuard(il2cpp);
        configureTextAssetHook(il2cpp);
        patchFileImports(il2cpp, kIl2CppName);
    }
    else breadcrumb("WARN", "core.io.result", "%s not loaded; imports not patched", kIl2CppName);
    patchFileImports(image, kUnityName);
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
    breadcrumb("INFO", "core.start", "recovery core: beta.1 loading flow; beta.2 managed hooks removed");
    pthread_t thread{};
    if (pthread_create(&thread, nullptr, startFontHook, nullptr) != 0) {
        LOGE("font worker creation failed"); breadcrumb("ERROR", "core.thread.failed", "pthread_create failed"); return;
    }
    pthread_detach(thread);
    LOGI("open-source translation core started"); breadcrumb("INFO", "core.started", "worker created");
}

}  // namespace

__attribute__((constructor)) static void lcpatchInit() { start(); }
