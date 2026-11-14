"""Prepare a process-local fontconfig file for bundled headless rendering on macOS."""
from pathlib import Path
import argparse
import shutil
from xml.sax.saxutils import escape


def prepare(runtime_root, output):
    selected = output / 'fonts'
    selected.mkdir(parents=True, exist_ok=True)
    sources = [
        (Path('/System/Library/Fonts/Supplemental/Songti.ttc'), 'songti-selected.ttc'),
        (Path('/System/Library/Fonts/STHeiti Medium.ttc'), 'heiti-selected.ttc'),
    ]
    for source, name in sources:
        if not source.exists():
            raise FileNotFoundError(f'Required local font unavailable: {source}')
        # Copy font bytes only; macOS protected-file metadata is not needed.
        shutil.copyfile(source, selected / name)
    latin = runtime_root / 'native/libreoffice-headless/libreoffice/LibreOfficeDev.app/Contents/Resources/fonts/truetype'
    if not latin.is_dir():
        raise FileNotFoundError(f'Bundled font directory unavailable: {latin}')
    config = ('<?xml version="1.0"?><fontconfig>'
              f'<dir>{escape(str(selected))}</dir><dir>{escape(str(latin))}</dir>'
              f'<cachedir>{escape(str(output / "font-cache"))}</cachedir>'
              '<alias><family>Arial</family><prefer><family>Liberation Sans</family></prefer></alias>'
              '<alias><family>Menlo</family><prefer><family>Liberation Mono</family></prefer></alias>'
              '</fontconfig>')
    path = output / 'fonts.conf'
    path.write_text(config)
    print(path)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime-root', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    prepare(args.runtime_root.resolve(), args.output_dir.resolve())
