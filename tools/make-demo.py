"""Make a faithful-time video from actual JavaFX screenshots, preserving dropped-frame timing."""
import pathlib, subprocess, sys, json
from PIL import Image
folder = pathlib.Path(sys.argv[1]).resolve()
output = pathlib.Path(sys.argv[2]).resolve()
captured = sorted(folder.glob('frame-*.png'))
frames, invalid = [], []
for frame in captured:
    try:
        with Image.open(frame) as image:
            image.verify()
        with Image.open(frame) as image:
            image.load()
        frames.append(frame)
    except Exception as error:
        invalid.append({'file': frame.name, 'reason': str(error)})
if not frames:
    raise SystemExit('No captured gameplay frames')
lines = []
for i, frame in enumerate(frames):
    current = int(frame.stem.split('-')[-1]) if i else 0
    next_index = int(frames[i + 1].stem.split('-')[-1]) if i + 1 < len(frames) else 243
    lines += ["file '" + frame.as_posix().replace("'", "'\\''") + "'", f'duration {max(1, next_index-current)/18:.9f}']
lines += ["file '" + frames[-1].as_posix().replace("'", "'\\''") + "'"]
manifest = folder / 'frames.ffconcat'
manifest.write_text('\n'.join(lines), encoding='utf-8')
output.parent.mkdir(parents=True, exist_ok=True)
complete = [int(frame.stem.split('-')[-1]) for frame in frames] == list(range(243))
inputs = ['-framerate', '18', '-start_number', '0', '-i', str(folder / 'frame-%05d.png')] if complete else ['-f', 'concat', '-safe', '0', '-i', str(manifest)]
subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'warning', '-xerror', '-y', *inputs, '-vf', 'scale=1280:720,fps=18,tpad=stop_mode=clone:stop_duration=1', '-frames:v', '243', '-c:v', 'libx264', '-crf', '20', '-pix_fmt', 'yuv420p', str(output)], check=True)
report = {'sourceDirectory': str(folder), 'capturedFiles': len(captured), 'validSourceFrames': len(frames), 'invalidSourceFrames': invalid, 'outputFrames': 243, 'fps': 18, 'durationSeconds': 13.5, 'encoding': 'direct_image_sequence' if complete else 'timestamped_concat', 'missingFramesHeldFromPreviousValidCapture': not complete}
(folder / 'encoding-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'{len(frames)} valid source frames; {len(invalid)} invalid skipped; encoding={report["encoding"]}; held_missing={not complete}; output={output}')
