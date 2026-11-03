"""Audio addresses out of video pages, for FlowFolio's Reading Atmosphere (ADR-0047).

Only the address is wanted: FlowFolio plays it with Android's own MediaPlayer, so nothing is downloaded here and
ffmpeg is never needed -- which is also what keeps this side of the app clear of GPL code (ADR-0003).
"""

import yt_dlp


def _from_formats(info):
    """The best audio-only format's address, newest-first as yt-dlp sorts worst-to-best."""
    for fmt in reversed(info.get("formats") or []):
        audio = fmt.get("acodec") not in (None, "none")
        video = fmt.get("vcodec") not in (None, "none")
        if audio and not video and fmt.get("url"):
            return fmt["url"]
    return None


def audio_url(page_url):
    """The address of [page_url]'s audio, or None.

    Live streams answer None on purpose (owner, M5 round 3): a live address goes stale while it is being read, and
    FlowFolio's own resolver already plays the HLS that live pages publish.
    """
    options = {
        "format": "bestaudio/best",
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        "socket_timeout": 20,
    }
    try:
        with yt_dlp.YoutubeDL(options) as ydl:
            info = ydl.extract_info(page_url, download=False)
    except Exception:
        return None
    if not info:
        return None
    if info.get("is_live") or info.get("live_status") in ("is_live", "post_live"):
        return None
    return _from_formats(info) or info.get("url")
