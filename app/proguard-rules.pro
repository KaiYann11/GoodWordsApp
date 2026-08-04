# R8 축소를 켠 릴리스 빌드용 규칙입니다.
# Room, Compose, WorkManager, Glance는 각 라이브러리가 consumer 규칙을 함께 배포하므로
# 그것만으로 부족한 것들만 여기에 둡니다.

# jsoup(링크 메타데이터 수집)은 서버 전용 클래스를 참조해 축소 과정에서 없는 클래스 경고가 난다.
-dontwarn org.jsoup.**

# 스택트레이스에서 줄 번호를 읽을 수 있게 남긴다.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
