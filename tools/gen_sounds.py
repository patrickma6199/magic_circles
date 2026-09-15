"""Generates the mod's sounds - assets/magiccircles/sounds/**/*.ogg - by synthesis rather than
recording. Needs numpy, scipy and soundfile (whose wheel bundles libsndfile with Ogg Vorbis):

    pip install numpy scipy soundfile

The Faye (entity/FairyEntity.java):
  fairy/chatter1-6    idle chatter: a tiny voice babbling in a tongue nobody speaks, half sung,
                      now and then breaking into a giggle, with a glassy chime riding on the words
  fairy/greet1-3      the same voice, brighter and asking, when a conversation opens (FairyTrades)
  fairy/hurt1-2       a strained "eek" with a crack of glass in it
  fairy/death         a falling cry that comes apart into a cascade of chimes
  fairy/flutter       a seamless loop of beating wings, played for as long as a fairy is in the air
                      (client/FairyFlutterSound.java)
  fairy/cast          a rising shimmer - a spell leaving a heartstone
  fairy/trade         a bright little arpeggio and a pleased giggle
  fairy/ward_raise    a glass-harmonica chord swelling up around the fairy (FairyWard)
  fairy/ward_break    the ward shattering into shards
  fairy/volley        ten wisps whistling out at once (WispMissileEntity)

Pixies (entity/PixieEntity.java) - quicker and higher than a fairy, and no words in it at all:
  pixie/chirp1-4      a twitter of little glass whistles
  pixie/hurt1-2       a sharp squeak, falling
  pixie/death         a trill that tumbles down and breaks into tiny bells

The Ferryman (entity/FerrymanEntity.java):
  ferryman/rumble1-3  a quiet, low rumble, like something very heavy settling somewhere far below

The two waters (block/WaterLikeFluidSounds.java, client/WaterLikeAmbientSounds.java) - each with an
enter splash, an exit, swim strokes, a submerge and a surface for the head going under and coming
up, a seamless underwater loop and a few rare additions on top of it:
  fluid/wellspring/*  the Wellspring: water with a cascade of glass chimes in it, and a warm, slowly
                      breathing chord underneath once you are under
  fluid/portal/*      the portal fluid: darker water with a pitch-bent swirl to it, a deep throb,
                      and a vortex droning under the surface

All mono (Minecraft only places mono sounds in the world) at 44.1 kHz. Each file is levelled by
loudness, not by peak - see write(): one-shots to about -20 dBFS RMS over their audible part and
loops to about -26 dBFS, with a hard cap on the peak - which puts them in the same range as the
game's own sounds, so the volumes passed in code mean what they do for vanilla sounds. Every sound
is seeded, so a rerun reproduces the files.
"""
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy import signal

SR = 44100
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/magiccircles/sounds"

# The pentatonic steps, in semitones, that the voice and the chimes keep to - it is what stops
# random notes from ever sounding wrong.
PENTATONIC = [0, 2, 4, 7, 9, 12]


# ----------------------------------------------------------------------------------------------
# Plumbing
# ----------------------------------------------------------------------------------------------

def samples(duration: float) -> int:
    return int(round(duration * SR))


def timeline(duration: float) -> np.ndarray:
    return np.arange(samples(duration)) / SR


def filt(x: np.ndarray, kind: str, freq, order: int = 4) -> np.ndarray:
    sos = signal.butter(order, freq, btype=kind, fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def place(canvas: np.ndarray, sound: np.ndarray, at: float) -> np.ndarray:
    """Mixes `sound` into `canvas` starting `at` seconds in, growing the canvas if it has to."""
    start = max(0, samples(at))
    end = start + len(sound)
    if end > len(canvas):
        canvas = np.concatenate([canvas, np.zeros(end - len(canvas))])
    canvas[start:end] += sound
    return canvas


def fades(x: np.ndarray, fade_in: float = 0.004, fade_out: float = 0.03) -> np.ndarray:
    x = x.copy()
    n_in, n_out = samples(fade_in), samples(fade_out)
    x[:n_in] *= np.linspace(0.0, 1.0, n_in)
    x[-n_out:] *= np.linspace(1.0, 0.0, n_out)
    return x


def normalise(x: np.ndarray, peak: float = 0.9) -> np.ndarray:
    return x * (peak / max(1e-9, float(np.max(np.abs(x)))))


def level(x: np.ndarray, rms_db: float, peak_cap: float = 0.5) -> np.ndarray:
    """Brings a sound to the given loudness - RMS over the part of it that is actually audible, so a
    long reverb tail doesn't make a short sound come out hot - then makes sure no peak passes the cap."""
    x = normalise(x, 1.0)
    frame = samples(0.05)
    frames = x[:len(x) // frame * frame].reshape(-1, frame)
    energy = np.sqrt(np.mean(frames ** 2, axis=1))
    audible = energy[energy > np.max(energy) * 0.05]
    rms = float(np.sqrt(np.mean(audible ** 2))) if len(audible) else float(np.sqrt(np.mean(x ** 2)))
    x = x * (10 ** (rms_db / 20) / max(1e-9, rms))
    peak = float(np.max(np.abs(x)))
    if peak > peak_cap:
        x = x * (peak_cap / peak)
    return x


def reverb(x: np.ndarray, rng, tail: float = 1.0, mix: float = 0.25, brightness: float = 6000) -> np.ndarray:
    """A small, airy space: convolution with exponentially dying, slightly darkened noise."""
    t = timeline(tail)
    impulse = rng.standard_normal(len(t)) * np.exp(-6.9 * t / tail)
    impulse = filt(impulse, "lowpass", brightness, 2)
    wet = signal.fftconvolve(x, impulse)
    wet *= np.max(np.abs(x)) / max(1e-9, float(np.max(np.abs(wet))))
    dry = np.concatenate([x, np.zeros(len(wet) - len(x))])
    return dry * (1.0 - mix) + wet * mix


def smooth_noise(rng, n: int, rate: float) -> np.ndarray:
    """A slow random wander in [-1, 1], changing at roughly `rate` Hz."""
    wander = filt(rng.standard_normal(n + SR), "lowpass", rate, 2)[SR:]
    return wander / max(1e-9, float(np.max(np.abs(wander))))


# ----------------------------------------------------------------------------------------------
# Chimes
# ----------------------------------------------------------------------------------------------

# (frequency ratio, gain, share of the length it takes to die away) - a small glass bell.
BELL_PARTIALS = [(1.0, 1.0, 1.0), (2.0, 0.3, 0.6), (2.76, 0.45, 0.45), (4.07, 0.2, 0.3),
                 (5.40, 0.18, 0.22), (8.93, 0.08, 0.12)]


def bell(freq: float, duration: float = 1.0) -> np.ndarray:
    t = timeline(duration)
    out = np.zeros_like(t)
    for ratio, gain, life in BELL_PARTIALS:
        f = freq * ratio
        if f < SR * 0.45:
            out += gain * np.sin(2 * np.pi * f * t) * np.exp(-t / (duration * life * 0.35))
    return out * np.minimum(1.0, t / 0.002)


def note(base: float, rng) -> float:
    return base * 2 ** (rng.choice(PENTATONIC) / 12)


# ----------------------------------------------------------------------------------------------
# The fairy voice
# ----------------------------------------------------------------------------------------------

# Formants - (centre Hz, width Hz, gain) - of a child's vowels, lifted and widened: the voice sits so
# high that its harmonics are far apart, and narrow formants would fall between them.
VOWELS = {
    "i": [(450, 300, 1.0), (3100, 500, 0.6), (3900, 600, 0.4)],
    "e": [(650, 300, 1.0), (2600, 500, 0.55), (3600, 600, 0.35)],
    "a": [(1050, 350, 1.0), (1700, 400, 0.7), (3300, 600, 0.3)],
    "o": [(700, 300, 1.0), (1150, 350, 0.55), (3200, 600, 0.2)],
    "u": [(480, 300, 1.0), (1100, 350, 0.35), (3000, 600, 0.15)],
}
CONSONANTS = [None, None, "t", "s", "h", "n", "l"]


def vowel_gain(freqs: np.ndarray, formants) -> np.ndarray:
    gain = np.full_like(freqs, 0.03)
    for centre, width, g in formants:
        gain += g / (1.0 + ((freqs - centre) / width) ** 2)
    return gain


def consonant(rng, kind, f0: float) -> np.ndarray:
    if kind == "s":
        t = timeline(0.05)
        return 0.3 * filt(rng.standard_normal(len(t)), "highpass", 5500) * np.sin(np.pi * t / 0.05) ** 2
    if kind == "t":
        t = timeline(0.012)
        return 0.6 * filt(rng.standard_normal(len(t)), "highpass", 3000) * np.exp(-t / 0.003)
    if kind == "h":
        t = timeline(0.045)
        return 0.22 * filt(rng.standard_normal(len(t)), "bandpass", [1200, 5000], 2) * (t / 0.045)
    if kind == "n":
        t = timeline(0.04)
        return 0.35 * np.sin(2 * np.pi * f0 * t) * (t / 0.04)
    return np.zeros(0)


def syllable(rng, f_start: float, f_end: float, duration: float, vowel: str, vowel_to: str | None = None,
             onset=None, vibrato: float = 0.025, breath: float = 0.08) -> np.ndarray:
    """One sung syllable: a glide in pitch and between two vowels, built harmonic by harmonic."""
    t = timeline(duration)
    shape = 0.5 - 0.5 * np.cos(np.pi * t / duration)
    f0 = f_start + (f_end - f_start) * shape
    f0 = f0 * (1 + vibrato * np.sin(2 * np.pi * rng.uniform(5.5, 8.0) * t + rng.uniform(0, 6.28))
               * np.minimum(1.0, t / 0.05))
    f0 = f0 * (1 - 0.06 * np.exp(-t / 0.02))  # the little scoop up into every note
    phase = 2 * np.pi * np.cumsum(f0) / SR
    a, b = VOWELS[vowel], VOWELS[vowel_to or vowel]

    voiced = np.zeros_like(t)
    for k in range(1, 25):
        fk = f0 * k
        if fk.max() > 9000:
            break
        gain = vowel_gain(fk, a) * (1 - shape) + vowel_gain(fk, b) * shape
        voiced += gain * np.sin(k * phase) / k ** 0.6
    voiced /= max(1e-9, float(np.max(np.abs(voiced))))
    voiced += breath * filt(rng.standard_normal(len(t)), "bandpass", [2500, 7000], 2)

    attack = 0.03 if onset == "l" else 0.012
    envelope = np.minimum(1.0, t / attack) * np.minimum(1.0, (duration - t) / 0.05)
    return np.concatenate([consonant(rng, onset, f_start), voiced * envelope])


def phrase(rng, count: int, base: float, asking: bool = False, sparkle: float = 0.3) -> np.ndarray:
    """A run of syllables - wandering up and down the scale, falling at the end, or rising if asking."""
    canvas = np.zeros(1)
    at = 0.0
    pitch = base * rng.uniform(0.95, 1.1)
    for i in range(count):
        last = i == count - 1
        duration = rng.uniform(0.07, 0.15) * (1.9 if last else 1.0)
        f_start = pitch
        pitch = float(np.clip(pitch * 2 ** (rng.choice([-3, -2, 0, 2, 3, 5]) / 12), base * 0.75, base * 1.6))
        f_end = f_start * (1.35 if asking else 0.8) if last else pitch
        vowel = rng.choice(list(VOWELS))
        vowel_to = rng.choice(list(VOWELS)) if rng.random() < 0.3 else None
        sound = syllable(rng, f_start, f_end, duration, vowel, vowel_to, rng.choice(CONSONANTS))
        canvas = place(canvas, sound * rng.uniform(0.7, 1.0), at)
        if rng.random() < sparkle:
            canvas = place(canvas, 0.18 * bell(note(base * 2, rng), 0.6), at + 0.01)
        at += len(sound) / SR + rng.uniform(-0.02, 0.05)
    return canvas


def giggle(rng, base: float, count: int | None = None) -> np.ndarray:
    """Hee-hee-hee: short breathy syllables tumbling down in pitch."""
    count = count or int(rng.integers(4, 7))
    canvas = np.zeros(1)
    at = 0.0
    pitch = base * 1.5
    for i in range(count):
        duration = rng.uniform(0.055, 0.075)
        sound = syllable(rng, pitch * 1.04, pitch * 0.96, duration, "i" if rng.random() < 0.7 else "e",
                         onset="h", vibrato=0.04, breath=0.2)
        canvas = place(canvas, sound * (1 - i * 0.1), at)
        at += len(sound) / SR + 0.03 + rng.uniform(0, 0.02)
        pitch *= 0.96
    return canvas


def hum(rng, base: float) -> np.ndarray:
    """A few notes hummed through closed lips, with a chime under each."""
    canvas = np.zeros(1)
    at = 0.0
    pitch = base
    for i in range(3):
        nxt = note(base * 0.9, rng)
        sound = syllable(rng, pitch, nxt, rng.uniform(0.25, 0.4), "u", onset="n" if i == 0 else "l", vibrato=0.035)
        canvas = place(canvas, sound, at)
        canvas = place(canvas, 0.15 * bell(nxt * 2, 0.8), at + 0.05)
        at += len(sound) / SR - 0.03
        pitch = nxt
    return canvas


# The fairy's speaking pitch - about three times a woman's.
VOICE = 640.0


def chatter(rng, kind: str) -> np.ndarray:
    if kind == "phrase":
        x = phrase(rng, int(rng.integers(3, 6)), VOICE, asking=rng.random() < 0.4)
    elif kind == "phrase_giggle":
        x = phrase(rng, int(rng.integers(2, 5)), VOICE)
        x = place(x, 0.8 * giggle(rng, VOICE), len(x) / SR + 0.05)
    elif kind == "giggle":
        x = giggle(rng, VOICE, 6)
    else:
        x = hum(rng, VOICE)
    return reverb(x, rng, tail=0.6, mix=0.18)


def greet(rng) -> np.ndarray:
    x = 0.25 * bell(note(1568, rng), 0.8)
    x = place(x, phrase(rng, int(rng.integers(4, 7)), VOICE * 1.05, asking=True, sparkle=0.35), 0.03)
    return reverb(x, rng, tail=0.7, mix=0.2)


def hurt(rng) -> np.ndarray:
    base = VOICE * 1.1 * rng.uniform(0.95, 1.05)
    x = syllable(rng, base * 1.7, base, 0.24, rng.choice(["a", "e"]), "e", onset="t", vibrato=0.05, breath=0.25)
    x = np.tanh(2.2 * x)  # strained
    x = place(x, 0.25 * bell(rng.uniform(3000, 4200), 0.35), 0.0)
    return reverb(x, rng, tail=0.4, mix=0.12)


def death(rng) -> np.ndarray:
    cry = syllable(rng, VOICE * 1.5, VOICE * 0.6, 0.7, "a", "o", vibrato=0.06, breath=0.2)
    x = cry * np.linspace(1.0, 0.3, len(cry))
    for i, f in enumerate([2093, 1760, 1568, 1319, 1175, 1047, 880, 784, 659, 587]):
        x = place(x, 0.3 * (1 - i * 0.06) * bell(f, 0.9), 0.25 + i * 0.075)
    for _ in range(25):
        x = place(x, 0.05 * bell(rng.uniform(3000, 7000), 0.3), rng.uniform(0.2, 1.1))
    return reverb(x, rng, tail=1.6, mix=0.35)


# ----------------------------------------------------------------------------------------------
# Fairy magic and wings
# ----------------------------------------------------------------------------------------------

def flutter(rng) -> np.ndarray:
    """One second of wingbeats that loops without a seam: every part of it repeats exactly once a
    second - noise built straight from a spectrum (so it is periodic by construction), a whole
    number of strokes, and shimmer tones with a whole number of cycles."""
    n = SR
    t = np.arange(n) / SR
    freqs = np.fft.rfftfreq(n, 1 / SR)

    def periodic_noise(centre: float, spread: float) -> np.ndarray:
        magnitude = np.exp(-((np.log(freqs + 1) - np.log(centre)) ** 2) / (2 * spread ** 2))
        spectrum = magnitude * np.exp(1j * rng.uniform(0, 2 * np.pi, len(freqs)))
        spectrum[0] = 0
        noise = np.fft.irfft(spectrum, n)
        return noise / np.std(noise)

    strokes = 26
    stroke = (t * strokes) % 1.0
    strength = 1 + 0.2 * rng.standard_normal(strokes)
    which = (t * strokes).astype(int) % strokes
    swish = (0.5 - 0.5 * np.cos(2 * np.pi * stroke)) ** 3 * strength[which]
    thrum = (0.5 - 0.5 * np.cos(2 * np.pi * stroke)) ** 6 * strength[which]

    x = 0.8 * periodic_noise(900, 0.55) * swish + 0.6 * periodic_noise(250, 0.35) * thrum
    for f, m in [(2600, 2), (3100, 3), (3700, 1), (4400, 2)]:
        x += 0.12 * np.sin(2 * np.pi * f * t) * (0.5 + 0.5 * np.sin(2 * np.pi * m * t + rng.uniform(0, 6.28)))
    return x


def cast(rng) -> np.ndarray:
    x = np.zeros(samples(1.3))
    for _ in range(40):
        at = rng.uniform(0, 0.8)
        centre = 900 * 2 ** (2 * at / 0.8)  # climbing two octaves over the cast
        x = place(x, rng.uniform(0.1, 0.25) * bell(note(centre, rng), rng.uniform(0.3, 0.6)), at)
    t = timeline(0.8)
    sweep = np.sin(2 * np.pi * np.cumsum(500 * 4 ** (t / 0.8)) / SR)
    x = place(x, 0.15 * sweep * np.sin(np.pi * t / 0.8) * (0.7 + 0.3 * np.sin(2 * np.pi * 12 * t)), 0.0)
    whoosh = filt(rng.standard_normal(len(t)), "bandpass", [1000, 6000], 2) * np.sin(np.pi * t / 0.8) ** 2
    x = place(x, 0.25 * whoosh, 0.0)
    return reverb(x, rng, tail=1.0, mix=0.3)


def ward_raise(rng) -> np.ndarray:
    duration = 1.8
    t = timeline(duration)
    swell = np.clip(t / 0.6, 0, 1) ** 2 * np.clip((duration - t) / 0.6, 0, 1)
    x = np.zeros_like(t)
    for f in [880.0, 1108.73, 1318.51, 1760.0]:
        x += np.sin(2 * np.pi * f * t) + 0.7 * np.sin(2 * np.pi * (f + 1.3) * t) + 0.15 * np.sin(4 * np.pi * f * t)
    x = 0.15 * x * swell
    x += 0.2 * filt(rng.standard_normal(len(t)), "bandpass", [400, 3000], 2) * np.clip(t / 0.6, 0, 1) * swell
    x = place(x, 0.3 * bell(3520, 1.2), 0.5)
    return reverb(x, rng, tail=1.4, mix=0.3)


def ward_break(rng) -> np.ndarray:
    t = timeline(0.2)
    x = 0.8 * filt(rng.standard_normal(len(t)), "highpass", 2500) * np.exp(-t / 0.03)
    for _ in range(45):
        at = min(0.6, rng.exponential(0.08))
        x = place(x, (0.35 - 0.4 * at) * bell(rng.uniform(2000, 8000), rng.uniform(0.15, 0.5)), at)
    t = timeline(0.6)
    fall = np.sin(2 * np.pi * np.cumsum(1760 * 0.5 ** (t / 0.6)) / SR) * np.linspace(1, 0, len(t)) ** 2
    x = place(x, 0.1 * fall, 0.02)
    return reverb(x, rng, tail=1.2, mix=0.3)


def volley(rng) -> np.ndarray:
    x = np.zeros(samples(1.0))
    t = timeline(0.4)
    for i in range(10):
        start, end = rng.uniform(600, 900), rng.uniform(2200, 3000)
        zip_ = np.sin(2 * np.pi * np.cumsum(start * (end / start) ** (t / 0.4)) / SR)
        air = filt(rng.standard_normal(len(t)), "bandpass", [1500, 5000], 2)
        envelope = np.minimum(1.0, t / 0.03) * np.exp(-t / 0.12)
        x = place(x, 0.2 * (0.5 * zip_ + air) * envelope, i * 0.035 + rng.uniform(0, 0.02))
    t = timeline(0.5)
    thrum = (np.sin(2 * np.pi * 220 * t) + 0.6 * np.sin(2 * np.pi * 330 * t)) * np.exp(-t / 0.1)
    x = place(x, 0.25 * thrum, 0.0)
    return reverb(x, rng, tail=0.8, mix=0.25)


def trade(rng) -> np.ndarray:
    x = np.zeros(1)
    for i, f in enumerate([1046.5, 1318.5, 1568.0, 2093.0]):
        x = place(x, 0.4 * bell(f, 0.9), i * 0.08)
    x = place(x, 0.15 * bell(2637.0, 0.7), 0.38)
    x = place(x, 0.5 * giggle(rng, VOICE, 3), 0.35)
    return reverb(x, rng, tail=0.9, mix=0.25)


# ----------------------------------------------------------------------------------------------
# Pixies
# ----------------------------------------------------------------------------------------------

def whistle(f_start: float, f_end: float, duration: float) -> np.ndarray:
    """A glassy little whistle: a pitch glide with a faint octave over it, swelling and fading."""
    t = timeline(duration)
    f = f_start * (f_end / f_start) ** (t / duration)
    phase = 2 * np.pi * np.cumsum(f) / SR
    return (np.sin(phase) + 0.3 * np.sin(2 * phase)) * np.sin(np.pi * t / duration) ** 1.5


def pixie_chirp(rng) -> np.ndarray:
    x = np.zeros(1)
    at = 0.0
    for _ in range(int(rng.integers(2, 5))):
        d = rng.uniform(0.05, 0.11)
        f0 = rng.uniform(1800, 2600)
        x = place(x, whistle(f0, f0 * rng.choice([1.3, 0.8, 1.5, 0.7]), d), at)
        x = place(x, 0.2 * bell(note(4186, rng), 0.3), at)
        at += d + rng.uniform(0.02, 0.06)
    return reverb(x, rng, tail=0.5, mix=0.2)


def pixie_hurt(rng) -> np.ndarray:
    x = whistle(rng.uniform(2800, 3400), rng.uniform(1200, 1600), 0.16)
    t = timeline(0.16)
    x += 0.25 * filt(rng.standard_normal(len(t)), "highpass", 4000, 2) * np.exp(-t / 0.04)
    return reverb(np.tanh(1.8 * x), rng, tail=0.4, mix=0.15)


def pixie_death(rng) -> np.ndarray:
    x = np.zeros(1)
    f = 3000.0
    at = 0.0
    for i in range(7):
        d = 0.06 + i * 0.015
        x = place(x, (1 - i * 0.08) * whistle(f, f * 0.8, d), at)
        at += d + 0.02
        f *= 0.85
    for _ in range(12):
        x = place(x, 0.15 * bell(rng.uniform(3000, 8000), 0.4), rng.uniform(0.3, 0.9))
    return reverb(x, rng, tail=1.2, mix=0.35)


# ----------------------------------------------------------------------------------------------
# The Ferryman
# ----------------------------------------------------------------------------------------------

def rumble(rng, duration: float = 4.5) -> np.ndarray:
    t = timeline(duration)
    n = len(t)
    ground = filt(filt(np.cumsum(rng.standard_normal(n)), "highpass", 18, 2), "lowpass", 110)
    ground = ground / np.max(np.abs(ground)) * (0.6 + 0.4 * smooth_noise(rng, n, 0.8))

    # The deep tone under it - two voices a fraction of a hertz apart, so it beats slowly, sinking a
    # little as it goes. Its overtones are what let it be heard on small speakers at all.
    f = rng.uniform(38, 46)
    phase = 2 * np.pi * np.cumsum(f * (1 - 0.04 * t / duration)) / SR
    tone = np.zeros(n)
    for k, gain in [(1, 1.0), (2, 0.5), (3, 0.3), (4, 0.15)]:
        tone += gain * (np.sin(k * phase) + 0.9 * np.sin(k * phase + 2 * np.pi * 0.35 * t))

    saw = 2 * ((phase * 2 / (2 * np.pi)) % 1.0) - 1
    groan = filt(saw, "lowpass", 260, 2) * np.sin(np.pi * t / duration) ** 3

    x = 0.9 * ground + 0.25 * tone + 0.35 * groan
    x = filt(x, "lowpass", 400, 2)
    # Nothing below 35 Hz: it is felt, not heard, and it is what pushes a speaker cone about.
    x = filt(x, "highpass", 35, 2)
    return x * np.sin(np.pi * t / duration) ** 1.5


# ----------------------------------------------------------------------------------------------
# The two waters
# ----------------------------------------------------------------------------------------------

def splash(rng, duration: float = 0.5, dark: bool = False) -> np.ndarray:
    """Water displaced: a burst of noise whose brightness falls away, with droplets pattering after."""
    t = timeline(duration)
    burst = rng.standard_normal(len(t)) * np.exp(-t / (duration * 0.25)) * np.minimum(1.0, t / 0.008)
    burst = filt(burst, "lowpass", 1500 if dark else 4000, 2) + 0.4 * filt(burst, "bandpass", [150, 600], 2)
    x = burst
    for _ in range(int(rng.integers(8, 16))):
        at = rng.uniform(0.08, duration)
        tt = timeline(0.05)
        f = rng.uniform(700, 1600) * (0.6 if dark else 1.0)
        drop = np.sin(2 * np.pi * np.cumsum(f * (1 + 0.6 * tt / 0.05)) / SR) * np.exp(-tt / 0.012)
        x = place(x, 0.25 * drop, at)
    return x


def stroke(rng, dark: bool = False) -> np.ndarray:
    t = timeline(0.35)
    x = rng.standard_normal(len(t)) * np.sin(np.pi * t / 0.35) ** 2
    return filt(x, "bandpass", [200, 1200] if dark else [300, 2500], 2)


def sweep(f_start: float, f_end: float, duration: float, shape: float = 1.0) -> np.ndarray:
    t = timeline(duration)
    f = f_start * (f_end / f_start) ** ((t / duration) ** shape)
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def flange(x: np.ndarray, rate: float, depth_ms: float = 4.0, base_ms: float = 1.0) -> np.ndarray:
    """A copy of the sound, delayed by a slowly wobbling few milliseconds, mixed back in - the
    hollow, swirling colour of a whirlpool."""
    t = np.arange(len(x)) / SR
    delay = (base_ms + depth_ms * (0.5 + 0.5 * np.sin(2 * np.pi * rate * t))) / 1000
    return x + np.interp(t - delay, t, x, left=0.0)


def periodic_noise(rng, n: int, low: float, high: float) -> np.ndarray:
    """Band-limited noise that repeats exactly every n samples - for seamless loops."""
    freqs = np.fft.rfftfreq(n, 1 / SR)
    band = ((freqs >= low) & (freqs <= high)).astype(float)
    band *= np.exp(-((np.log(freqs + 1) - np.log(np.sqrt(low * high))) ** 2) / 0.8)
    spectrum = band * np.exp(1j * rng.uniform(0, 2 * np.pi, len(freqs)))
    spectrum[0] = 0
    noise = np.fft.irfft(spectrum, n)
    return noise / np.std(noise)


def fold(canvas: np.ndarray, n: int) -> np.ndarray:
    """Wraps anything spilling past n samples back round to the start, so a loop stays seamless."""
    out = np.zeros(n)
    for i in range(0, len(canvas), n):
        chunk = canvas[i:i + n]
        out[:len(chunk)] += chunk
    return out


# --- Wellspring -------------------------------------------------------------------------------

def glass_cascade(rng, count: int, base: float, rising: bool, spread: float, gain: float = 0.3) -> np.ndarray:
    x = np.zeros(1)
    steps = sorted(rng.choice(PENTATONIC + [14, 16, 19], count), reverse=not rising)
    for i, step in enumerate(steps):
        x = place(x, gain * (1 - i * 0.4 / count) * bell(base * 2 ** (step / 12), rng.uniform(0.6, 1.2)),
                  i * spread + rng.uniform(0, spread * 0.3))
    return x


def sparkle(rng, duration: float, density: float = 60, gain: float = 0.06) -> np.ndarray:
    x = np.zeros(samples(duration))
    for _ in range(int(duration * density)):
        x = place(x, gain * bell(rng.uniform(3000, 9000), rng.uniform(0.1, 0.3)), rng.uniform(0, duration))
    return x


def glass_chord(freqs, duration: float, attack: float, release: float) -> np.ndarray:
    t = timeline(duration)
    env = np.clip(t / attack, 0, 1) ** 2 * np.clip((duration - t) / release, 0, 1) ** 2
    x = np.zeros_like(t)
    for f in freqs:
        x += np.sin(2 * np.pi * f * t) + 0.6 * np.sin(2 * np.pi * (f * 1.002) * t) + 0.1 * np.sin(4 * np.pi * f * t)
    return x * env / len(freqs)


def wellspring_enter(rng) -> np.ndarray:
    x = 0.8 * splash(rng, 0.6)
    x = place(x, glass_cascade(rng, 7, 1046.5, rising=True, spread=0.05), 0.02)
    x = place(x, sparkle(rng, 0.9), 0.05)
    return reverb(x, rng, tail=1.0, mix=0.25)


def wellspring_exit(rng) -> np.ndarray:
    x = 0.5 * splash(rng, 0.45)
    x = place(x, glass_cascade(rng, 5, 1568.0, rising=False, spread=0.07, gain=0.22), 0.0)
    for _ in range(6):  # the drips running off, each a glass bead
        x = place(x, 0.15 * bell(rng.uniform(2500, 5000), 0.3), rng.uniform(0.3, 0.9))
    return reverb(x, rng, tail=0.8, mix=0.2)


def wellspring_swim(rng) -> np.ndarray:
    x = 0.7 * stroke(rng)
    for _ in range(int(rng.integers(1, 3))):
        x = place(x, 0.2 * bell(note(2093, rng), 0.5), rng.uniform(0.02, 0.15))
    return x


def wellspring_submerge(rng) -> np.ndarray:
    t = timeline(0.3)
    whump = filt(rng.standard_normal(len(t)), "lowpass", 500, 2) * np.exp(-t / 0.08)
    x = 0.6 * whump
    x = place(x, 0.5 * glass_chord([523.25, 659.25, 783.99, 1046.5], 1.6, 0.4, 0.8), 0.05)
    x = place(x, sparkle(rng, 1.2, density=40), 0.1)
    return reverb(x, rng, tail=1.2, mix=0.35)


def wellspring_surface(rng) -> np.ndarray:
    x = 0.5 * glass_chord([1046.5, 1318.5, 1568.0], 0.5, 0.02, 0.4)
    t = timeline(0.4)
    air = filt(rng.standard_normal(len(t)), "highpass", 2000, 2) * np.minimum(1, t / 0.02) * np.exp(-t / 0.1)
    x = place(x, 0.5 * air, 0.0)
    x = place(x, 0.4 * splash(rng, 0.3), 0.0)
    return reverb(x, rng, tail=0.6, mix=0.15)


def wellspring_loop(rng, duration: float = 8.0) -> np.ndarray:
    n = samples(duration)
    t = np.arange(n) / SR
    # A warm chord breathing in and out, exactly once per loop, over a soft underwater hush.
    breath = 0.65 + 0.35 * np.sin(2 * np.pi * t / duration - np.pi / 2)
    x = 0.5 * glass_chord([130.8, 196.0, 261.6, 392.0, 523.25], duration, 0.001, 0.001) * breath
    x += 0.35 * periodic_noise(rng, n, 60, 500)
    x += 0.08 * periodic_noise(rng, n, 2000, 7000) * (0.5 + 0.5 * np.sin(2 * np.pi * 3 * t / duration))
    events = np.zeros(1)
    for _ in range(14):  # motes of light chiming as they drift past
        events = place(events, 0.12 * bell(note(1046.5, rng), rng.uniform(0.8, 1.5)), rng.uniform(0, duration))
    return x + fold(events, n)


def wellspring_addition(rng) -> np.ndarray:
    x = glass_cascade(rng, int(rng.integers(3, 6)), rng.choice([523.25, 784.0, 1046.5]), rising=rng.random() < 0.5,
                      spread=rng.uniform(0.15, 0.3), gain=0.25)
    return reverb(x, rng, tail=2.0, mix=0.5)


# --- Portal fluid -----------------------------------------------------------------------------

def throb(freq: float, duration: float, gain: float = 1.0) -> np.ndarray:
    t = timeline(duration)
    return gain * (np.sin(2 * np.pi * freq * t) + 0.4 * np.sin(4 * np.pi * freq * t)) * np.exp(-t / (duration * 0.3))


def portal_enter(rng) -> np.ndarray:
    x = 0.8 * splash(rng, 0.6, dark=True)
    x = place(x, 0.35 * sweep(900, 140, 0.7, 0.6) * np.exp(-timeline(0.7) / 0.35), 0.0)
    x = place(x, 0.5 * throb(55, 0.8), 0.02)
    x = flange(x, 1.5, 6.0)
    return reverb(x, rng, tail=1.2, mix=0.3, brightness=2500)


def portal_exit(rng) -> np.ndarray:
    t = timeline(0.6)
    rise = sweep(150, 1200, 0.6, 1.4) * np.sin(np.pi * t / 0.6) ** 2
    x = 0.35 * rise
    # Sucked back out: a swell that cuts off rather than dies away, like a sound played backwards.
    tt = timeline(0.35)
    swell = filt(rng.standard_normal(len(tt)), "bandpass", [300, 2000], 2) * (tt / 0.35) ** 2
    x = place(x, 0.6 * swell, 0.0)
    x = place(x, 0.5 * splash(rng, 0.4, dark=True), 0.33)
    x = flange(x, 2.0, 5.0)
    return reverb(x, rng, tail=0.9, mix=0.25, brightness=3000)


def portal_swim(rng) -> np.ndarray:
    x = 0.7 * stroke(rng, dark=True)
    t = timeline(0.3)
    x = place(x, 0.15 * sweep(rng.uniform(600, 900), rng.uniform(200, 350), 0.3) * np.sin(np.pi * t / 0.3) ** 2, 0.03)
    return flange(x, 3.0, 4.0)


def portal_submerge(rng) -> np.ndarray:
    x = 0.7 * throb(45, 1.4)
    t = timeline(1.6)
    centre = 400 * 2 ** (1.5 * np.sin(2 * np.pi * 1.2 * t))
    vortex = filt(rng.standard_normal(len(t)), "bandpass", [150, 2500], 2) * np.sin(2 * np.pi * np.cumsum(centre) / SR)
    x = place(x, 0.35 * vortex * np.clip(t / 0.5, 0, 1) * np.clip((1.6 - t) / 0.6, 0, 1), 0.0)
    x = place(x, 0.25 * sweep(1400, 200, 1.2, 0.5) * np.exp(-timeline(1.2) / 0.5), 0.05)
    return reverb(x, rng, tail=1.5, mix=0.35, brightness=2000)


def portal_surface(rng) -> np.ndarray:
    t = timeline(0.5)
    x = 0.4 * sweep(200, 1600, 0.5, 1.3) * np.sin(np.pi * t / 0.5) ** 2
    pop = filt(rng.standard_normal(samples(0.12)), "bandpass", [400, 3000], 2) * np.exp(-timeline(0.12) / 0.03)
    x = place(x, 0.7 * pop, 0.3)
    x = place(x, 0.3 * throb(60, 0.4), 0.3)
    return reverb(flange(x, 2.5, 4.0), rng, tail=0.7, mix=0.2, brightness=3000)


def portal_loop(rng, duration: float = 8.0) -> np.ndarray:
    n = samples(duration)
    t = np.arange(n) / SR
    # Two deep tones slowly beating, and a vortex of noise whose pitch circles once every two seconds.
    x = 0.4 * (np.sin(2 * np.pi * 55 * t) + np.sin(2 * np.pi * 55.5 * t) + 0.5 * np.sin(2 * np.pi * 82.5 * t))
    x *= 0.8 + 0.2 * np.sin(2 * np.pi * 2 * t / duration)
    noise = periodic_noise(rng, n, 100, 3000)
    circles = 4
    centre = 350 * 2 ** (1.2 * np.sin(2 * np.pi * circles * t / duration))
    x += 0.2 * noise * np.sin(2 * np.pi * np.cumsum(centre) / SR) + 0.2 * periodic_noise(rng, n, 40, 300)
    whistles = np.zeros(1)
    for _ in range(5):
        d = rng.uniform(0.8, 1.5)
        tt = timeline(d)
        w = sweep(rng.uniform(500, 900), rng.uniform(150, 300), d, 0.8) * np.sin(np.pi * tt / d) ** 2
        whistles = place(whistles, 0.08 * w, rng.uniform(0, duration))
    return flange(x + fold(whistles, n), 1.0, 5.0)


def portal_addition(rng) -> np.ndarray:
    d = rng.uniform(1.5, 2.5)
    t = timeline(d)
    x = 0.4 * sweep(rng.uniform(300, 600), rng.uniform(80, 150), d, 0.7) * np.sin(np.pi * t / d) ** 2
    x += 0.1 * filt(rng.standard_normal(len(t)), "bandpass", [200, 1500], 2) * np.sin(np.pi * t / d) ** 2
    return reverb(flange(x, 0.7, 8.0), rng, tail=2.5, mix=0.5, brightness=1500)


# ----------------------------------------------------------------------------------------------

ONE_SHOT_DB = -20.0
LOOP_DB = -26.0


def write(name: str, x: np.ndarray, gain_db: float = 0.0, loop: bool = False) -> None:
    """`gain_db` nudges a sound above or below the standard level for its kind (see level())."""
    path = OUT / f"{name}.ogg"
    path.parent.mkdir(parents=True, exist_ok=True)
    if not loop:
        x = fades(x)
    x = level(x, (LOOP_DB if loop else ONE_SHOT_DB) + gain_db, peak_cap=0.35 if loop else 0.5)
    sf.write(path, x.astype(np.float32), SR, format="OGG", subtype="VORBIS")
    print(f"wrote {path.relative_to(OUT.parent.parent.parent.parent.parent.parent)}  "
          f"({len(x) / SR:.2f}s, peak {np.max(np.abs(x)):.2f})")


def main() -> None:
    for i, kind in enumerate(["phrase", "phrase_giggle", "giggle", "phrase", "hum", "phrase_giggle"], 1):
        write(f"fairy/chatter{i}", chatter(np.random.default_rng(100 + i), kind), -3.0)
    for i in range(1, 4):
        write(f"fairy/greet{i}", greet(np.random.default_rng(200 + i)), -2.0)
    for i in range(1, 3):
        write(f"fairy/hurt{i}", hurt(np.random.default_rng(300 + i)))
    write("fairy/death", death(np.random.default_rng(400)))
    write("fairy/flutter", flutter(np.random.default_rng(500)), loop=True)
    write("fairy/cast", cast(np.random.default_rng(600)))
    write("fairy/trade", trade(np.random.default_rng(700)))
    write("fairy/ward_raise", ward_raise(np.random.default_rng(800)))
    write("fairy/ward_break", ward_break(np.random.default_rng(900)))
    write("fairy/volley", volley(np.random.default_rng(1000)))
    for i in range(1, 5):
        write(f"pixie/chirp{i}", pixie_chirp(np.random.default_rng(1200 + i)), -4.0)
    for i in range(1, 3):
        write(f"pixie/hurt{i}", pixie_hurt(np.random.default_rng(1300 + i)), -2.0)
    write("pixie/death", pixie_death(np.random.default_rng(1400)), -2.0)
    for i in range(1, 4):
        # Quiet by design - and levelled quieter still, since the ear discounts bass anyway.
        write(f"ferryman/rumble{i}", rumble(np.random.default_rng(1100 + i), duration=4.0 + i * 0.5), -4.0)

    waters = {
        "wellspring": (wellspring_enter, wellspring_exit, wellspring_swim, wellspring_submerge,
                       wellspring_surface, wellspring_loop, wellspring_addition),
        "portal": (portal_enter, portal_exit, portal_swim, portal_submerge,
                   portal_surface, portal_loop, portal_addition),
    }
    for seed, (name, (enter, exit_, swim, submerge, surface, loop, addition)) in enumerate(waters.items()):
        base = 2000 + seed * 100
        for i in range(1, 3):
            write(f"fluid/{name}/enter{i}", enter(np.random.default_rng(base + i)))
            write(f"fluid/{name}/exit{i}", exit_(np.random.default_rng(base + 10 + i)))
        for i in range(1, 4):
            write(f"fluid/{name}/swim{i}", swim(np.random.default_rng(base + 20 + i)), -4.0)
            write(f"fluid/{name}/addition{i}", addition(np.random.default_rng(base + 30 + i)), -6.0)
        write(f"fluid/{name}/submerge", submerge(np.random.default_rng(base + 40)))
        write(f"fluid/{name}/surface", surface(np.random.default_rng(base + 41)))
        write(f"fluid/{name}/loop", loop(np.random.default_rng(base + 42)), loop=True)


if __name__ == "__main__":
    main()
