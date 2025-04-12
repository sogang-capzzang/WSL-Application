# WSL-Application

설치 및 실행

1. Git 클론

```
git clone https://github.com/sogang-capzzang/WSL-Application.git
```

2. IntelliJ IDEA에서 프로젝트 열기

- File > Open > 프로젝트 디렉토리 선택

3. 안드로이드 SDK 설치

- File > Project Structure > SDKs로 이동
- '+' 버튼 > Android SDK 선택
- SDK가 없으면 Download 버튼으로 설치 (최소 API 24, 권장 34)

4. local.properties 생성

- 루트 디렉토리에 local.properties 파일이 없으면 만들고 아래의 sdk 경로가 있는지 확인

```
sdk.dir=/path/to/android-sdk
```
5. local.properties에 api key 추가


```
gemini.api.key=your-api-key
```

5. assets 폴더 생성
app/src/main/assets/에 아래 구조로 디렉토리와 파일을 추가




```
app/src/main/assets/
├── person/
│   ├── woon.jpg
│   ├── minjoon.jpg
│   └── gyeongyeon.png
├── exercise/
│   ├── woon/
│   │   ├── exercise1.mp4
│   │   ├── exercise2.mp4
│   │   └── exercise3.mp4
│   ├── minjoon/
│   │   ├── exercise1.mp4
│   │   ├── exercise2.mp4
│   │   └── exercise3.mp4
│   └── gyeongyeon/
│       ├── exercise1.mp4
│       ├── exercise2.mp4
│       └── exercise3.mp4
└── lipsync/
    ├── woon/
    │   ├── lipsync1.mp4
    │   ├── lipsync2.mp4
    │   └── lipsync3.mp4
    ├── minjoon/
    │   ├── lipsync1.mp4
    │   ├── lipsync2.mp4
    │   └── lipsync3.mp4
    └── gyeongyeon/
        ├── lipsync1.mp4
        ├── lipsync2.mp4
        └── lipsync3.mp4
```
