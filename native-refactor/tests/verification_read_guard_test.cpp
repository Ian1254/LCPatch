#include "verification_read_guard.h"
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <string>
#include <thread>

extern "C" char verification_begin[], verification_end[];
lcpatch::VerificationRange range;
lcpatch::VerificationRange imageRange;
std::string official, translated;
unsigned checks = 0;
#define CHECK(value) do { ++checks; if (!(value)) { std::fprintf(stderr, "FAIL line %d: %s\n", __LINE__, #value); std::exit(1); } } while(false)

__attribute__((noinline)) std::string selectedRead() {
    const bool verification = lcpatch::isResourceVerificationRead(range, imageRange);
    std::ifstream input(verification ? official : translated);
    return {std::istreambuf_iterator<char>(input), std::istreambuf_iterator<char>()};
}

__attribute__((noinline)) std::string verificationRead() {
    asm volatile(".global verification_begin\nverification_begin:" ::: "memory");
    auto result = selectedRead();
    asm volatile(".global verification_end\nverification_end:" ::: "memory");
    return result;
}

int main(int argc, char **argv) {
    CHECK(argc == 2);
    official = std::string(argv[1]) + "/official.json";
    translated = std::string(argv[1]) + "/translation.json";
    std::ofstream(official) << "official data used for integrity verification";
    std::ofstream(translated) << "translated text with different bytes and length";
    range = {reinterpret_cast<uintptr_t>(verification_begin), reinterpret_cast<uintptr_t>(verification_end)};
    CHECK(range.end > range.begin);
    CHECK(!lcpatch::isResourceVerificationRead({}));
    CHECK(!lcpatch::isResourceVerificationRead({2, 1}));
    for (unsigned restart = 0; restart < 3; ++restart) {
        CHECK(verificationRead() == "official data used for integrity verification");
        CHECK(selectedRead() == "translated text with different bytes and length");
    }
    std::string verified, displayed;
    std::thread verifier([&] { verified = verificationRead(); });
    std::thread loader([&] { displayed = selectedRead(); });
    verifier.join(); loader.join();
    CHECK(verified == "official data used for integrity verification");
    CHECK(displayed == "translated text with different bytes and length");
    std::ifstream input(official);
    CHECK(std::string(std::istreambuf_iterator<char>(input), {}) == "official data used for integrity verification");
    std::printf("PASS: %u checks; real unwind differentiates verification/text reads across three cycles and concurrent threads; original bytes unchanged\n", checks);
    return 0;
}
