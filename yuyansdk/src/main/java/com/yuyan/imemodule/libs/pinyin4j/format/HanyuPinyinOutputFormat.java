package com.yuyan.imemodule.libs.pinyin4j.format;

final public class HanyuPinyinOutputFormat {

  public HanyuPinyinOutputFormat() {
    restoreDefault();
  }

  
  public void restoreDefault() {
    vCharType = HanyuPinyinVCharType.WITH_U_AND_COLON;
    caseType = HanyuPinyinCaseType.LOWERCASE;
    toneType = HanyuPinyinToneType.WITH_TONE_NUMBER;
  }

  
  public HanyuPinyinCaseType getCaseType() {
    return caseType;
  }

  
  public void setCaseType(HanyuPinyinCaseType caseType) {
    this.caseType = caseType;
  }

  
  public HanyuPinyinToneType getToneType() {
    return toneType;
  }

  
  public void setToneType(HanyuPinyinToneType toneType) {
    this.toneType = toneType;
  }

  
  public HanyuPinyinVCharType getVCharType() {
    return vCharType;
  }

  
  public void setVCharType(HanyuPinyinVCharType charType) {
    vCharType = charType;
  }

  private HanyuPinyinVCharType vCharType;

  private HanyuPinyinCaseType caseType;

  private HanyuPinyinToneType toneType;

}
