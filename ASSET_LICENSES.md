# Ресурсы и лицензии

В версии 0.7.0 меню, портреты, эмблема, анатомическая основа и региональная текстура используют собственные материалы проекта. Файлы и точные запросы для новых изображений: [docs/ART_PROVENANCE.md](docs/ART_PROVENANCE.md). Эмблема создана из собственной SVG-геометрии.

Пользовательские изображения и видео references остаются локальными. Maven исключает assets/reference/**; публичный репозиторий также исключает оригиналы. Старое извлечение портретов, фотографий предметов и анатомии из референсов заменено. В активном HUD снова используется собственная нейтральная фигура `assets/medical/neutral-wireframe-v1.png` с цветом по данным 13 областей тела. План, двери, предметы, подписи, маршруты, биомасса и телеметрия строятся из состояния игры.

Текстура `assets/terrain/region-neutral-v1.png` создана встроенным ImageGen по географической части B. Задание: тёмная зелёно-чёрная карта лесов, дорог и поселений строго сверху, без текста, рамок, значков, маршрутов и зон опасности. Узлы, подписи и биологическая зона добавляются из Campaign. Эта текстура — иллюстрация, не реальная географическая карта.

Значки, маршруты, модель роста и сеть биомассы созданы кодом. OriginalScore и AudioBus синтезируют оригинальную тёмную эмбиентную музыку и семь типов эффектов: интерфейс, шаги, шлюз, лечение, тревога, голосование, принятое решение. Воспроизведение — стерео PCM 48 кГц. Сторонних музыкальных композиций, записей и аудиосэмплов нет. Предпрослушивание можно создать командой OriginalScore.main; готовый пример хранится в artifacts/audio-preview.wav.

Шрифт JetBrains Mono: [официальный репозиторий](https://github.com/JetBrains/JetBrainsMono), SIL Open Font License 1.1, файл OFL.txt включён рядом с TTF и внутрь JAR. Системные шрифты, когда используются как fallback, из Windows не копируются в дистрибутив.

Roboto Condensed Regular/Bold: [официальный репозиторий Roboto 2](https://github.com/googlefonts/roboto-2), Apache License 2.0, `Roboto-static-LICENSE.txt` рядом с TTF. Дополнительный variable Roboto Condensed: [Google Fonts](https://github.com/google/fonts/tree/main/ofl/robotocondensed), SIL OFL 1.1; в текущем HUD используется статическая версия. Share Tech Mono: [Google Fonts](https://github.com/google/fonts/tree/main/ofl/sharetechmono), SIL OFL 1.1, `ShareTechMono-OFL.txt` включён.

Компоненты: OpenJFX 25.0.2 (GPLv2 + Classpath Exception), Jackson 2.20.2 (Apache-2.0), SLF4J 2.0.17 (MIT), Logback 1.5.32 (EPL/LGPL), Maven Wrapper 3.3.4 (Apache-2.0). Их LICENSE/NOTICE находятся в поставляемых JAR либо исходных официальных дистрибутивах. JUnit используется только для тестов и в игровой пакет не входит.

Встроенный runtime: Eclipse Temurin OpenJDK 25.0.4.1+1, GPLv2 + Classpath Exception. Оригинальные legal/notice файлы runtime сохранены в дистрибутиве. Источник, размер и ожидаемый SHA-256 архива зафиксированы в .runtime/temurin-package.json; архив проверен перед распаковкой. [Исходники и выпуск Temurin](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.4.1%2B1).

Подписание Windows-кода сертификатом не выполнялось. Собственные тексты/код проекта не получают автоматически выдуманную коммерческую лицензию из этого документа.
