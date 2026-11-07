#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>

namespace lcpatch {

// Executable segments of the loaded libil2cpp.so, not an approximate min/max
// interval (which could include inaccessible gaps).
struct SignatureScanRange {
    uintptr_t begin = 0;
    uintptr_t end = 0;
};

struct VerificationSignature {
    const uint8_t *entryBytes = nullptr;
    size_t entrySize = 0;
    size_t methodSize = 0;
    size_t fileOpenCallOffset = 0;
    // nullptr is an exact match for known Build IDs. A mask is permitted only
    // for read-only discovery of a previously analyzed method in unknown builds.
    const uint8_t *entryMask = nullptr;
};

struct VerificationSignatureMatch {
    uintptr_t address = 0;
    size_t signatureIndex = 0;
    unsigned candidateCount = 0;
    bool scanComplete = false;
};

inline bool matchesVerificationEntry(const uint8_t *actual,
                                     const VerificationSignature &signature) {
    if (!actual || !signature.entryBytes || !signature.entrySize) return false;
    if (!signature.entryMask)
        return std::memcmp(actual, signature.entryBytes, signature.entrySize) == 0;
    for (size_t i = 0; i < signature.entrySize; ++i) {
        if (((actual[i] ^ signature.entryBytes[i]) & signature.entryMask[i]) != 0)
            return false;
    }
    return true;
}

inline bool containsVerificationRange(const SignatureScanRange *ranges,
                                      size_t count, uintptr_t at, size_t length) {
    if (!ranges || at > UINTPTR_MAX - length) return false;
    for (size_t i = 0; i < count; ++i)
        if (at >= ranges[i].begin && at + length <= ranges[i].end) return true;
    return false;
}

// Check the independent FileStream-constructor call site. A BL-shaped word
// alone is insufficient: its resolved target must lie in executable code,
// outside the candidate verification method itself. This does not prove the
// callee's identity; it is a conservative additional structural constraint.
inline bool validVerificationCall(const SignatureScanRange *ranges, size_t rangeCount,
                                  uintptr_t entry, const VerificationSignature &signature,
                                  const uint8_t *candidateBytes, size_t available) {
    if (!candidateBytes || signature.fileOpenCallOffset > available ||
        available - signature.fileOpenCallOffset < sizeof(uint32_t) ||
        !containsVerificationRange(ranges, rangeCount, entry, signature.methodSize) ||
        signature.fileOpenCallOffset >= signature.methodSize) return false;
    uint32_t word = 0;
    std::memcpy(&word, candidateBytes + signature.fileOpenCallOffset, sizeof(word));
    if ((word & 0xfc000000U) != 0x94000000U) return false; // AArch64 BL
    const int64_t displacement = static_cast<int64_t>(static_cast<int32_t>(word << 6)) >> 4;
    const uintptr_t site = entry + signature.fileOpenCallOffset;
    uintptr_t target = 0;
    if (displacement < 0) {
        const auto distance = static_cast<uintptr_t>(-displacement);
        if (site < distance) return false;
        target = site - distance;
    } else {
        const auto distance = static_cast<uintptr_t>(displacement);
        if (site > UINTPTR_MAX - distance) return false;
        target = site + distance;
    }
    return (target & 3U) == 0 &&
           (target < entry || target >= entry + signature.methodSize) &&
           containsVerificationRange(ranges, rangeCount, target, sizeof(uint32_t));
}

inline bool validatesVerificationCandidate(const SignatureScanRange *ranges,
                                           size_t rangeCount, uintptr_t entry,
                                           const VerificationSignature &signature,
                                           const uint8_t *candidateBytes, size_t available) {
    if (!candidateBytes || !signature.entryBytes || !signature.entrySize ||
        signature.methodSize < signature.entrySize ||
        signature.fileOpenCallOffset > 512U - sizeof(uint32_t) ||
        signature.entrySize > 512U || available < signature.entrySize) return false;
    return containsVerificationRange(ranges, rangeCount, entry, signature.methodSize) &&
           matchesVerificationEntry(candidateBytes, signature) &&
           validVerificationCall(ranges, rangeCount, entry, signature, candidateBytes, available);
}

// Reader(address, destination, length) returns false on inaccessible pages.
// Never accept a scan with unreadable ranges, more than one *distinct* match,
// or bounds outside the executable segments. All candidate validation is
// shared with the later lazy check before actually redirecting file reads.
template <typename Reader>
VerificationSignatureMatch findUniqueVerificationSignature(
    const SignatureScanRange *ranges, size_t rangeCount,
    const VerificationSignature *signatures, size_t signatureCount, Reader reader) {
    VerificationSignatureMatch result{};
    if (!ranges || !signatures || !rangeCount || !signatureCount) return result;

    constexpr size_t kChunk = 64 * 1024;
    constexpr size_t kLookahead = 512;
    constexpr size_t kMaxScanBytes = 256U * 1024U * 1024U;
    uint8_t buffer[kChunk + kLookahead];
    size_t totalBytes = 0;

    for (size_t s = 0; s < signatureCount; ++s) {
        const auto &sig = signatures[s];
        if (!sig.entryBytes || !sig.entrySize || sig.entrySize > kLookahead ||
            sig.fileOpenCallOffset > kLookahead - sizeof(uint32_t) ||
            sig.methodSize < sig.entrySize ||
            sig.methodSize < sig.fileOpenCallOffset + sizeof(uint32_t))
            return result;
    }

    for (size_t r = 0; r < rangeCount; ++r) {
        const auto &range = ranges[r];
        if (!range.begin || range.end <= range.begin ||
            range.begin > UINTPTR_MAX - 3) return result;
        const size_t length = range.end - range.begin;
        if (length > kMaxScanBytes - totalBytes) return result;
        totalBytes += length;
        uintptr_t cursor = (range.begin + 3) & ~uintptr_t(3);
        while (cursor < range.end) {
            const size_t remaining = range.end - cursor;
            const size_t advance = remaining < kChunk ? remaining : kChunk;
            const size_t requested = remaining < kChunk + kLookahead ?
                remaining : kChunk + kLookahead;
            if (!reader(cursor, buffer, requested)) return result;
            for (size_t offset = 0; offset < advance; offset += 4) {
                const uintptr_t candidate = cursor + offset;
                for (size_t s = 0; s < signatureCount; ++s) {
                    const auto &sig = signatures[s];
                    const size_t needed = sig.fileOpenCallOffset + sizeof(uint32_t) >
                        sig.entrySize ? sig.fileOpenCallOffset + sizeof(uint32_t) : sig.entrySize;
                    if (requested - offset < needed ||
                        !validatesVerificationCandidate(
                            ranges, rangeCount, candidate, sig,
                            buffer + offset, requested - offset)) continue;
                    if (result.address == candidate) continue; // two equivalent profiles
                    if (++result.candidateCount != 1) {
                        result.address = 0; // Ambiguous: fail closed.
                        return result;
                    }
                    result.address = candidate;
                    result.signatureIndex = s;
                }
            }
            cursor += advance;
        }
    }
    result.scanComplete = true;
    return result;
}

} // namespace lcpatch
