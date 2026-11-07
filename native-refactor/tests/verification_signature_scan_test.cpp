#include "../verification_signature_scan.h"
#include <array>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#define CHECK(v) do { if (!(v)) { std::fprintf(stderr, "FAIL line %d: %s\\n", __LINE__, #v); std::exit(1); } ++checks; } while (false)

int main() {
    unsigned checks = 0;
    constexpr std::array<uint8_t, 32> knownMethodA = {
        0xff, 0x43, 0x01, 0xd1, 0xfe, 0x5f, 0x02, 0xa9,
        0xf6, 0x57, 0x03, 0xa9, 0xf4, 0x4f, 0x04, 0xa9,
        0xb7, 0xcd, 0x02, 0x90, 0x16, 0x95, 0x02, 0xf0,
        0xf4, 0x03, 0x02, 0x2a, 0xe8, 0x4a, 0x71, 0x39
    };
    constexpr std::array<uint8_t, 32> knownMethodB = {
        0xff, 0x43, 0x01, 0xd1, 0xfe, 0x5f, 0x02, 0xa9,
        0xf6, 0x57, 0x03, 0xa9, 0xf4, 0x4f, 0x04, 0xa9,
        0x57, 0xcd, 0x02, 0xd0, 0xb6, 0x94, 0x02, 0xd0,
        0xf4, 0x03, 0x02, 0x2a, 0xe8, 0x7a, 0x71, 0x39
    };
    constexpr size_t kMethodSize = 0x1ec;
    constexpr size_t kFileStreamCall = 0x88;
    const lcpatch::VerificationSignature signatures[] = {
        {knownMethodA.data(), knownMethodA.size(), kMethodSize, kFileStreamCall},
        {knownMethodB.data(), knownMethodB.size(), kMethodSize, kFileStreamCall}
    };
    std::array<uint32_t, 1024> memory{};
    uint8_t *bytes = reinterpret_cast<uint8_t *>(memory.data());
    const auto base = reinterpret_cast<uintptr_t>(bytes);
    lcpatch::SignatureScanRange executable[] = {{base, base + sizeof(memory)}};
    auto reader = [](uintptr_t at, uint8_t *dest, size_t len) {
        std::memcpy(dest, reinterpret_cast<const void *>(at), len);
        return true;
    };
    auto scan = [&] { return lcpatch::findUniqueVerificationSignature(
        executable, 1, signatures, 2, reader); };
    auto place = [&](size_t offset, const std::array<uint8_t, 32> &pattern, bool validCall) {
        std::memcpy(bytes + offset, pattern.data(), pattern.size());
        const uint32_t instruction = validCall ? 0x94000000U : 0xd503201fU;
        std::memcpy(bytes + offset + kFileStreamCall, &instruction, sizeof(instruction));
    };

    place(0x100, knownMethodA, true);
    auto match = scan();
    CHECK(match.scanComplete && match.candidateCount == 1);
    CHECK(match.address == base + 0x100 && match.signatureIndex == 0);

    // The same verifier binary moves after an otherwise unrelated update.
    std::memset(bytes, 0, sizeof(memory));
    place(0x200, knownMethodB, true);
    match = scan();
    CHECK(match.scanComplete && match.candidateCount == 1);
    CHECK(match.address == base + 0x200 && match.signatureIndex == 1);

    // Never accept an invalid file-open call, near-match, or ambiguous entry.
    bytes[0x200 + kFileStreamCall] = 0;
    match = scan();
    CHECK(match.scanComplete && match.candidateCount == 0 && match.address == 0);

    std::memset(bytes, 0, sizeof(memory));
    place(0x100, knownMethodA, true);
    place(0x400, knownMethodA, true);
    match = scan();
    CHECK(match.scanComplete && match.candidateCount > 1 && match.address == 0);

    std::memset(bytes, 0, sizeof(memory));
    place(0xe80, knownMethodA, true);
    match = scan();
    CHECK(match.scanComplete && match.candidateCount == 0 && match.address == 0);

    // Unreadable executable pages must not be treated as a successful search.
    match = lcpatch::findUniqueVerificationSignature(
        executable, 1, signatures, 2,
        [](uintptr_t, uint8_t *, size_t) { return false; });
    CHECK(!match.scanComplete && match.address == 0);

    // Invalid signatures/ranges are rejected without accessing memory.
    const lcpatch::SignatureScanRange invalid[] = {{0, 100}};
    match = lcpatch::findUniqueVerificationSignature(invalid, 1, signatures, 2, reader);
    CHECK(!match.scanComplete && match.address == 0);

    std::printf("PASS: %u verifier-signature compatibility checks\\n", checks);
    return 0;
}
