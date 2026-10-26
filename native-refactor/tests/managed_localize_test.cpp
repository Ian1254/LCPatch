#include "managed_localize.h"

#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <memory>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>
#include <sys/stat.h>

namespace {
unsigned checks = 0;
#define CHECK(condition) do { ++checks; if (!(condition)) { \
    std::fprintf(stderr, "FAIL line %d: %s\n", __LINE__, #condition); std::exit(1); } } while (false)
struct String { std::u16string value; };
std::vector<std::unique_ptr<String>> strings;
void *makeString(const char *text) {
    auto value = std::make_unique<String>();
    while (*text) value->value += static_cast<unsigned char>(*text++);
    void *result = value.get();
    strings.push_back(std::move(value));
    return result;
}
std::string textOf(void *value) {
    const auto &text = static_cast<String *>(value)->value;
    return std::string(text.begin(), text.end());
}
std::string read(const std::string &path) {
    std::ifstream stream(path);
    return {std::istreambuf_iterator<char>(stream), std::istreambuf_iterator<char>()};
}
void write(const std::string &path, const char *text) { std::ofstream(path) << text; }

void *request = nullptr;
const void *tag = reinterpret_cast<void *>(0x1234);
void *instance = reinterpret_cast<void *>(0x5678);
void *cancellation = reinterpret_cast<void *>(0x9876);
std::string pending;
bool shouldThrow = false;

void *readText(void *path, const void *method) {
    CHECK(method == tag);
    return makeString(read(textOf(path)).c_str());
}
void *readAsync(void *path, lcpatch::CancellationToken token, const void *method) {
    CHECK(method == tag && token.source == cancellation);
    pending = textOf(path);
    return instance;
}
void *loadSync(void *self, void *name, const void *method) {
    CHECK(self == instance && name == request && method == tag);
    if (shouldThrow) throw std::runtime_error("managed loader failure");
    return lcpatch::hookedReadText(name, method);
}
void loadAsync(void *self, int32_t concurrent, const void *method) {
    CHECK(self == instance && concurrent == 3 && method == tag);
    CHECK(lcpatch::hookedReadTextAsync(request, {cancellation}, method) == instance);
}
}

int main(int argc, char **argv) {
    CHECK(argc == 2);
    const std::string root = argv[1];
    CHECK(mkdir(root.c_str(), 0700) == 0);
    std::string directory = root;
    for (const char *part : {"files", "Assets", "Resources_moved", "Localize", "jp"}) {
        directory += std::string("/") + part;
        CHECK(mkdir(directory.c_str(), 0700) == 0);
    }
    const std::string official = directory + "/JP_Test.json";
    const std::string translated = root + "/translated";
    CHECK(mkdir(translated.c_str(), 0700) == 0);
    write(official, "{\"text\":\"official-v1\"}");
    write(translated + "/JP_Test.json", "{\"text\":\"translation\"}");
    write(translated + "/EN_Test.json", "english-target");
    write(translated + "/KR_Test.json", "korean-target");
    CHECK(mkdir((translated + "/sub").c_str(), 0700) == 0);
    write(translated + "/sub/JP_Nested.json", "nested");
    const std::string expected = read(official);
    auto &api = lcpatch::managedLocalize;
    api.runtimeDirectory = translated.c_str();
    api.stringLength = [](void *s) { return static_cast<int32_t>(static_cast<String *>(s)->value.size()); };
    api.stringChars = [](void *s) { return static_cast<String *>(s)->value.c_str(); };
    api.stringNew = makeString;
    api.loadSync = loadSync;
    api.loadAsync = loadAsync;
    api.readText = readText;
    api.readTextAsync = readAsync;
    request = makeString(official.c_str());

    // An unsupported/partially installed runtime always keeps originals.
    CHECK(textOf(lcpatch::hookedLoadSync(instance, request, tag)) == expected);
    api.ready.store(true);
    for (int launch = 0; launch < 3; ++launch) {
        CHECK(textOf(lcpatch::hookedReadText(request, tag)) == expected);
        CHECK(textOf(lcpatch::hookedLoadSync(instance, request, tag)) == "{\"text\":\"translation\"}");
        CHECK(lcpatch::localizeReadDepth == 0);
        CHECK(read(official) == expected);
    }
    // Paths are substituted before Task creation; its eventual worker needs
    // no scope, while a checksum on that same worker still sees the original.
    lcpatch::hookedLoadAsync(instance, 3, tag);
    CHECK(lcpatch::localizeReadDepth == 0);
    CHECK(pending == translated + "/JP_Test.json");
    std::string eventual;
    std::thread task([&] { eventual = read(pending); });
    task.join();
    CHECK(eventual == "{\"text\":\"translation\"}");
    lcpatch::hookedReadTextAsync(request, {cancellation}, tag);
    CHECK(pending == official);

    // An active load on one thread cannot change a concurrent verifier.
    std::string verified;
    {
        lcpatch::LocalizeReadScope outer;
        {
            lcpatch::LocalizeReadScope inner;
            CHECK(lcpatch::localizeReadDepth == 2);
        }
        CHECK(lcpatch::localizeReadDepth == 1);
        std::thread verifier([&] {
            CHECK(lcpatch::localizeReadDepth == 0);
            verified = textOf(lcpatch::hookedReadText(request, tag));
        });
        verifier.join();
        CHECK(verified == expected);
    }
    shouldThrow = true;
    try { lcpatch::hookedLoadSync(instance, request, tag); CHECK(false); }
    catch (const std::runtime_error &) {}
    shouldThrow = false;
    CHECK(lcpatch::localizeReadDepth == 0);
    CHECK(textOf(lcpatch::hookedReadText(request, tag)) == expected);

    // Updates are visible to verifiers and never overwritten by the module.
    write(official, "{\"text\":\"official-v2\"}");
    CHECK(textOf(lcpatch::hookedReadText(request, tag)) == read(official));
    CHECK(textOf(lcpatch::hookedLoadSync(instance, request, tag)) == "{\"text\":\"translation\"}");
    CHECK(read(official) == "{\"text\":\"official-v2\"}");
    {
        lcpatch::LocalizeReadScope scope;
        for (const char *valid : {"Localize/en/EN_Test.json", "Localize/kr/KR_Test.json", "Localize/jp/sub/JP_Nested.json"}) {
            void *path = makeString(valid);
            CHECK(lcpatch::selectManagedPath(path) != path);
        }
        CHECK(lcpatch::selectManagedPath(nullptr) == nullptr);
        void *missing = makeString((directory + "/JP_Missing.json").c_str());
        CHECK(lcpatch::selectManagedPath(missing) == missing);
        void *unicode = makeString(official.c_str());
        static_cast<String *>(unicode)->value[0] = u'漢';
        CHECK(lcpatch::selectManagedPath(unicode) == unicode);
        for (const char *invalid : {
            "Localize/jp/../JP_Test.json", "Localize/jp/./JP_Test.json",
            "Localize/jp/sub/../JP_Test.json", "Localize/jp//JP_Test.json",
            "Localize/xx/JP_Test.json", "Localize/jp/EN_Test.json",
            "Localize/jp/RemoteLocalizeFileList.json", "Localize/jp/ChineseFont.ttf",
            "/another/Localize/jp/JP_Test.json"}) {
            void *path = makeString(invalid);
            CHECK(lcpatch::selectManagedPath(path) == path);
        }
        char small[1];
        CHECK(!lcpatch::buildLocalizeRedirect(official.c_str(), translated.c_str(), small, sizeof(small)));
        auto saved = api.stringNew;
        api.stringNew = [](const char *) -> void * { return nullptr; };
        CHECK(lcpatch::selectManagedPath(request) == request);
        api.stringNew = saved;
    }
    CHECK(lcpatch::localizeReadDepth == 0);
    std::printf("PASS: %u checks; sync/async text substitution, three verifier/load cycles, concurrent verification, exception cleanup, update and fallback\n", checks);
    return 0;
}
