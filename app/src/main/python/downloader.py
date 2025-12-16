import yt_dlp
import os

progress_data = {"status": "idle", "downloaded": 0, "total": 0, "filename": ""}

def my_hook(d):
    global progress_data
    if d['status'] == 'downloading':
        progress_data["status"] = "downloading"
        progress_data["downloaded"] = d.get('downloaded_bytes', 0)
        progress_data["total"] = d.get('total_bytes', 0) or d.get('total_bytes_estimate', 0)
        progress_data["filename"] = d.get('filename', "")
    elif d['status'] == 'finished':
        progress_data["status"] = "finished"
        progress_data["filename"] = d.get('filename', "")

def download_audio(url, output_dir):
    opts = {
        'format': 'bestaudio[ext=m4a]/bestaudio/best',
        'outtmpl': os.path.join(output_dir, '%(title)s.%(ext)s'),
        'noplaylist': True,
        'progress_hooks': [my_hook],
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=True)
        filename = ydl.prepare_filename(info)
        return filename

def get_progress():
    return progress_data
