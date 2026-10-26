// A tiny IL2CPP API fixture for exercising the real ARM64 resolver and Dobby
// trampolines in a separate verification APK, without modifying the game.
#include <jni.h>
#include <unistd.h>
#include <sys/stat.h>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <stdexcept>
#include <string>
#include <vector>
#include <atomic>

struct String { std::u16string text; };
struct Token { void *source; };
struct Class { const char *space; const char *name; };
struct Type { int kind; Class *klass = nullptr; };
struct Method { void *pointer; const char *name; uint32_t flags; uint32_t count; Type *result; Type *parameters[2]; };
Class manager{"TextAssetPatch", "TextAssetPatchManager"}, loader{"TextAssetPatch", "TextAssetAsyncLoader"};
Class file{"System.IO", "File"}, tokenClass{"System.Threading", "CancellationToken"};
Class taskClass{"System.Threading.Tasks", "Task`1"};
Type stringType{0x0e}, intType{0x08}, voidType{0x01}, tokenType{0x11, &tokenClass}, taskType{0x15, &taskClass};
String *pending = nullptr;
bool raiseException = false;
std::string requested;
std::atomic<bool> initialized{false};
std::atomic<unsigned> domainCalls{0};

extern "C" String *il2cpp_string_new(const char *text) {
    auto *value = new String;
    while (*text) value->text += static_cast<unsigned char>(*text++);
    return value;
}
extern "C" int32_t il2cpp_string_length(String *value) { return static_cast<int32_t>(value->text.size()); }
extern "C" const char16_t *il2cpp_string_chars(String *value) { return value->text.c_str(); }
std::string text(String *value) { return {value->text.begin(), value->text.end()}; }
std::string read(const std::string &path) {
    std::ifstream stream(path);
    return {std::istreambuf_iterator<char>(stream), std::istreambuf_iterator<char>()};
}
__attribute__((noinline)) String *readText(String *path, const void *) { return il2cpp_string_new(read(text(path)).c_str()); }
__attribute__((noinline)) void *readTextAsync(String *path, Token, const void *) { pending = path; return path; }
__attribute__((noinline)) String *loadSync(void *, String *name, const void *) {
    if (raiseException) throw std::runtime_error("fixture exception");
    return readText(name, nullptr);
}
__attribute__((noinline)) void loadAsync(void *, int32_t, const void *) {
    readTextAsync(il2cpp_string_new(requested.c_str()), {nullptr}, nullptr);
}
Method methods[] = {
    {reinterpret_cast<void *>(loadSync), "LoadLocalizeFileSync", 0x86, 1, &stringType, {&stringType, nullptr}},
    {reinterpret_cast<void *>(loadAsync), "ProcessLoadAsync", 0x86, 1, &voidType, {&intType, nullptr}},
    {reinterpret_cast<void *>(readText), "ReadAllText", 0x96, 1, &stringType, {&stringType, nullptr}},
    {reinterpret_cast<void *>(readTextAsync), "ReadAllTextAsync", 0x96, 2, &taskType, {&stringType, &tokenType}},
};
extern "C" void *il2cpp_domain_get() {
    ++domainCalls;
    if (!initialized.load()) std::abort();
    return &manager;
}
extern "C" void *il2cpp_thread_current() { return initialized.load() ? &manager : nullptr; }
extern "C" __attribute__((noinline)) void *il2cpp_runtime_invoke(const void *, void *object, void **, void **) { return object; }
extern "C" const void **il2cpp_domain_get_assemblies(void *, size_t *count) {
    static const void *assemblies[] = {&manager}; *count = 1; return assemblies;
}
extern "C" const void *il2cpp_assembly_get_image(const void *assembly) { return assembly; }
extern "C" Class *il2cpp_class_from_name(const void *, const char *space, const char *name) {
    for (Class *candidate : {&manager, &loader, &file})
        if (!std::strcmp(candidate->space, space) && !std::strcmp(candidate->name, name)) return candidate;
    return nullptr;
}
extern "C" Method *il2cpp_class_get_methods(Class *klass, void **iterator) {
    const auto offset = reinterpret_cast<uintptr_t>(*iterator);
    const size_t count = klass == &file ? 2 : 1;
    if (offset >= count) return nullptr;
    *iterator = reinterpret_cast<void *>(offset + 1);
    return &methods[(klass == &manager ? 0 : klass == &loader ? 1 : 2) + offset];
}
extern "C" const char *il2cpp_method_get_name(Method *method) { return method->name; }
extern "C" uint32_t il2cpp_method_get_param_count(Method *method) { return method->count; }
extern "C" Type *il2cpp_method_get_param(Method *method, uint32_t index) { return method->parameters[index]; }
extern "C" Type *il2cpp_method_get_return_type(Method *method) { return method->result; }
extern "C" uint32_t il2cpp_method_get_flags(Method *method, uint32_t *implementation) { *implementation = 0; return method->flags; }
extern "C" int il2cpp_type_get_type(Type *type) { return type->kind; }
extern "C" Class *il2cpp_class_from_type(Type *type) { return type->klass; }
extern "C" const char *il2cpp_class_get_name(Class *klass) { return klass->name; }
extern "C" const char *il2cpp_class_get_namespace(Class *klass) { return klass->space; }
extern "C" int32_t il2cpp_class_value_size(Class *, uint32_t *alignment) { *alignment = 8; return 8; }
extern "C" void *il2cpp_thread_attach(void *domain) { return domain; }
extern "C" void il2cpp_thread_detach(void *) {}

extern "C" JNIEXPORT jstring JNICALL Java_com_lcpatch_verification_MainActivity_runChecks(JNIEnv *env, jobject) {
    const std::string root = "/data/user/0/com.lcpatch.issue87verification/files";
    std::string directory = root;
    for (const char *part : {"Assets", "Resources_moved", "Localize", "jp"}) {
        directory += std::string("/") + part; mkdir(directory.c_str(), 0700);
    }
    requested = directory + "/JP_Test.json";
    std::ofstream(requested) << "official";
    const std::string translated = root + "/translated";
    mkdir(translated.c_str(), 0700);
    std::ofstream(translated + "/JP_Test.json") << "translation";
    auto fail = [&](const char *reason) { return env->NewStringUTF(reason); };
    String *name = il2cpp_string_new(requested.c_str());
    usleep(300000);
    il2cpp_runtime_invoke(nullptr, nullptr, nullptr, nullptr);
    if (domainCalls.load()) return fail("FAIL: domain queried before runtime initialization");
    initialized.store(true);
    bool hooked = false;
    for (unsigned n = 0; n < 300; ++n) {
        il2cpp_runtime_invoke(nullptr, nullptr, nullptr, nullptr);
        if (text(loadSync(nullptr, name, nullptr)) == "translation") { hooked = true; break; }
        usleep(100000);
    }
    if (!hooked) return fail("FAIL: real ARM64 managed hooks never became ready");
    for (unsigned n = 0; n < 3; ++n) {
        if (text(readText(name, nullptr)) != "official") return fail("FAIL: verifier was redirected");
        if (text(loadSync(nullptr, name, nullptr)) != "translation") return fail("FAIL: sync load");
    }
    loadAsync(nullptr, 3, nullptr);
    if (!pending || read(text(pending)) != "translation") return fail("FAIL: async captured path");
    readTextAsync(name, {nullptr}, nullptr);
    if (text(pending) != requested) return fail("FAIL: ordinary async read was redirected");
    raiseException = true;
    try { loadSync(nullptr, name, nullptr); }
    catch (const std::runtime_error &) {}
    raiseException = false;
    if (text(readText(name, nullptr)) != "official") return fail("FAIL: scope leaked after exception");
    if (read(requested) != "official") return fail("FAIL: original was modified");
    return env->NewStringUTF("PASS: real ARM64 ELF export lookup, signature resolver, invocation readiness gate, Dobby hooks, sync/async substitution, verifier/load cycles, exception cleanup; game untouched");
}
