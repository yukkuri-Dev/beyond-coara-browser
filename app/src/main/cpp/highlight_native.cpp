#include <jni.h>
#include <string>
#include <vector>
#include <regex>
#include <algorithm>

struct HighlightSpan {
    int start;
    int end;
    int color;
};

extern "C" {

JNIEXPORT jobjectArray JNICALL
Java_com_coara_browser_htmlview_diffHighlightNative(JNIEnv* env, jclass clazz,
                                                      jstring oldTextJ, jstring newTextJ) {

    const char* oldTextC = env->GetStringUTFChars(oldTextJ, nullptr);
    const char* newTextC = env->GetStringUTFChars(newTextJ, nullptr);
    std::string oldText(oldTextC), newText(newTextC);
    env->ReleaseStringUTFChars(oldTextJ, oldTextC);
    env->ReleaseStringUTFChars(newTextJ, newTextC);


    int diffStart = 0;
    int minLen = std::min(oldText.size(), newText.size());
    while (diffStart < minLen && oldText[diffStart] == newText[diffStart]) {
        diffStart++;
    }

    int diffEndOld = oldText.size();
    int diffEndNew = newText.size();
    while (diffEndOld > diffStart && diffEndNew > diffStart &&
           oldText[diffEndOld - 1] == newText[diffEndNew - 1]) {
        diffEndOld--;
        diffEndNew--;
    }

    
    if(diffStart >= diffEndNew){
         diffStart = 0;
         diffEndNew = newText.size();
    }

    std::string diffSegment = newText.substr(diffStart, diffEndNew - diffStart);


    const int tagColor       = 0xFF0000FF;  
    const int attributeColor = 0xFF008000;  
    const int valueColor     = 0xFFB22222; 


    std::regex tag_regex("<[^>]+>");
    std::regex attr_regex("(\\w+)=\\\"([^\\\"]*)\\\"");
    std::vector<HighlightSpan> spans;

    auto tag_begin = std::sregex_iterator(diffSegment.begin(), diffSegment.end(), tag_regex);
    auto tag_end = std::sregex_iterator();
    for (auto it = tag_begin; it != tag_end; ++it) {
        std::smatch tagMatch = *it;
        int tagStart = static_cast<int>(tagMatch.position()) + diffStart;
        int tagEndPos = tagStart + static_cast<int>(tagMatch.length());
        spans.push_back({ tagStart, tagEndPos, tagColor });

        std::string tagText = tagMatch.str();
        auto attr_begin = std::sregex_iterator(tagText.begin(), tagText.end(), attr_regex);
        auto attr_end = std::sregex_iterator();
        for (auto ait = attr_begin; ait != attr_end; ++ait) {
            std::smatch attrMatch = *ait;
            int attrNameStart = tagStart + static_cast<int>(attrMatch.position(1));
            int attrNameEnd = attrNameStart + static_cast<int>(attrMatch.length(1));
            spans.push_back({ attrNameStart, attrNameEnd, attributeColor });
            int attrValueStart = tagStart + static_cast<int>(attrMatch.position(2));
            int attrValueEnd = attrValueStart + static_cast<int>(attrMatch.length(2));
            spans.push_back({ attrValueStart, attrValueEnd, valueColor });
        }
    }

    jclass intArrayClass = env->FindClass("[I");
    jobjectArray jresult = env->NewObjectArray(static_cast<jsize>(spans.size()), intArrayClass, nullptr);
    for (size_t i = 0; i < spans.size(); i++) {
        jint arr[3] = { spans[i].start, spans[i].end, spans[i].color };
        jintArray intArr = env->NewIntArray(3);
        env->SetIntArrayRegion(intArr, 0, 3, arr);
        env->SetObjectArrayElement(jresult, static_cast<jsize>(i), intArr);
        env->DeleteLocalRef(intArr);
    }
    return jresult;
}

}
