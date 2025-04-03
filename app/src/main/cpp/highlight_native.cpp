#include <jni.h>
#include <string>
#include <vector>
#include <regex>

struct HighlightSpan {
    int start;
    int end;
    int color;
};

extern "C" {

JNIEXPORT jobjectArray JNICALL
Java_com_coara_browser_HighlightNative_highlightHtmlNative(JNIEnv* env, jclass clazz, jstring htmlStr) {
  
    const char* cstr = env->GetStringUTFChars(htmlStr, nullptr);
    std::string html(cstr);
    env->ReleaseStringUTFChars(htmlStr, cstr);

  
    const int tagColor       = 0xFF0000FF; 
    const int attributeColor = 0xFF008000; 
    const int valueColor     = 0xFFB22222; 

    
    std::regex tag_regex("<[^>]+>");
    std::regex attr_regex("(\\w+)=\\\"([^\\\"]*)\\\"");

    std::vector<HighlightSpan> spans;

  
    auto tag_begin = std::sregex_iterator(html.begin(), html.end(), tag_regex);
    auto tag_end = std::sregex_iterator();
    for (auto it = tag_begin; it != tag_end; ++it) {
        std::smatch tagMatch = *it;
        int tagStart = static_cast<int>(tagMatch.position());
        int tagEndPos = tagStart + static_cast<int>(tagMatch.length());
        spans.push_back({ tagStart, tagEndPos, tagColor });

      
        std::string tagText = tagMatch.str();
        auto attr_begin = std::sregex_iterator(tagText.begin(), tagText.end(), attr_regex);
        auto attr_end = std::sregex_iterator();
        for (auto ait = attr_begin; ait != attr_end; ++ait) {
            std::smatch attrMatch = *ait;
            int attrNameStart = tagStart + static_cast<int>(attrMatch.position(1));
            int attrNameEnd   = attrNameStart + static_cast<int>(attrMatch.length(1));
            spans.push_back({ attrNameStart, attrNameEnd, attributeColor });
            int attrValueStart = tagStart + static_cast<int>(attrMatch.position(2));
            int attrValueEnd   = attrValueStart + static_cast<int>(attrMatch.length(2));
            spans.push_back({ attrValueStart, attrValueEnd, valueColor });
        }
    }

    jclass intArrayClass = env->FindClass("[I");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(spans.size()), intArrayClass, nullptr);
    for (size_t i = 0; i < spans.size(); i++) {
        jint arr[3] = { spans[i].start, spans[i].end, spans[i].color };
        jintArray intArr = env->NewIntArray(3);
        env->SetIntArrayRegion(intArr, 0, 3, arr);
        env->SetObjectArrayElement(result, static_cast<jsize>(i), intArr);
        env->DeleteLocalRef(intArr);
    }
    return result;
}

} 
