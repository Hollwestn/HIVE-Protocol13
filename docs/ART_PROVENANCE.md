# Оригинальная графика HIVE: PROTOCOL 13

Версия 0.7.0. Изображения меню и трёх персонажей созданы встроенным ImageGen 29.10.2026. Исходные PNG сохранены без ретуши. Они не являются скриншотами игрового процесса. Снимки в руководстве получены из работающего JavaFX-клиента 0.7.0.

## Меню

- Основной файл: `assets/brand/menu-background-v1.png`, 1672 × 941.
- Копия в клиенте: `game-client/src/main/resources/assets/brand/menu-background-v1.png`.
- Поверх картинки расположены настоящие кнопки. Игровая карта строится отдельно из серверного состояния.

Точный запрос к встроенному ImageGen:

> Use case: stylized-concept. Asset type: finished background illustration for the main menu of HIVE: PROTOCOL 13, a native 2D cooperative bunker horror game. Create an original razor-sharp 2D top-down technical blueprint / archival scanner illustration on nearly black emerald paper. Wide landscape 16:9, ideally 1920x1080 or higher. Composition: the left 44 percent is very dark quiet negative space reserved for real menu controls, with only a faint technical grid. In the right half, a dense overhead bunker plan of connected rectangular rooms, narrow corridors, electrical traces and concentric scanning arcs dissolves into fine branching dark-crimson biological filaments along the far right and lower edge. Flat orthographic plan only, absolutely no perspective, no 3D objects or renders. Disciplined fine engraved lines, crisp drafting geometry, restrained pale desaturated mint-green linework against #030b08 and #10261e, subtle paper grain, tiny muted red contamination accents, ominous scientific restraint. Keep a strong silhouette and clear delicate details, no bloom, no fog, no blur, no vignette that erases details. No text, numbers, lettering, logos, typography, characters, UI buttons, screenshots, watermark or border. This is atmospheric original menu art, not a playable map and not a copy of any existing design.

## Персонажи

- `assets/brand/portraits-v1.png`, 1536 × 1024, три равные колонки.
- Копия в клиенте: `game-client/src/main/resources/assets/brand/portraits-v1.png`.
- Слева Сокол, в центре Волкова, справа Коршун. HUD использует соответствующий viewport атласа.

Точный запрос:

> Use case: illustration-story. Asset type: original three-character portrait atlas for HIVE: PROTOCOL 13, a dark 2D bunker terminal game. A single wide sheet in THREE equal vertical panels side by side, exact equal widths, separated only by dark empty space; each panel contains one closely framed front-facing head-and-shoulders portrait, entire head and shoulders inside the panel, consistent head size and lighting. LEFT: fictional male engineer about 40, short dark hair, worn utility collar, composed wary expression. CENTER: fictional female medic-biologist about 30, short practical hair, high medical field-jacket collar, resolute expression. RIGHT: fictional male reconnaissance specialist about 28, narrow face, close-cropped hair, light tactical collar, watchful expression. Original unrelated fictional faces, not photographs or celebrities. High-quality flat 2D charcoal engraving / monochrome archival ink illustration, delicate crisp crosshatching, deep black-green background, pale desaturated mint-gray highlights, muted emerald shadows; ominous understated scientific horror, serious adults. Visually coherent across all three panels, modest rim light, no 3D, no glossy rendering, no blur, no bloom. No weapons, no masks covering faces, no helmet, no letters, no numbers, no emblems, no text, no captions, no watermark. Full canvas landscape 1536x1024; no subject may cross between panels.

## Эмблема и значки

`tools/create-brand-assets.py` рисует собственную геометрическую эмблему: шестиугольный корпус, три капсулы, соединяющая перемычка и красная отметка. Исходник: `assets/brand/hive-icon.svg`. PNG 512 × 512 и Windows ICO 16–256 пикселей созданы из этого SVG. Это векторная графика, ImageGen для иконки не применялся. Предметы и условные обозначения карты рисуются `TerminalDraw`.

## Прежние иллюстрации

Нейтральная медицинская фигура `assets/medical/neutral-wireframe-v1.png` и фоновая текстура региона `assets/terrain/region-neutral-v1.png` созданы встроенным ImageGen на ранних этапах проекта. Медицинские показатели, цвет областей тела, подписи, маршруты и угрозы отображаются из текущих игровых данных. Текстура региона иллюстративная.

Пользовательские референсы сохранены локально. Их исходные изображения и видео, а также извлечённые из них фрагменты не входят в публичный репозиторий и пакет 0.7.0.
