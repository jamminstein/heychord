// Engine_SundayService.sc
// Choir voice engine for HiChord gospel-hip-hop automation
// Inspired by Kanye West's Sunday Service / Jesus Is King sonic palette
// SATB choir voices with vibrato, formant shaping, breath texture, and lush space

Engine_SundayService : CroneEngine {

  var <voices;       // Dictionary: note -> array of synths (one per voice part)
  var <reverbSynth;
  var <delaySynth;
  var <fxBus, <delayBus;
  var voiceGroup, fxGroup;
  var reverb_mix, delay_mix, delay_time, delay_fb;
  var gain_val;
  var <choirSize;    // 1=quartet, 2=ensemble, 3=full choir

  alloc {
    voices = Dictionary.new;
    reverb_mix = 0.45;
    delay_mix = 0.15;
    delay_time = 0.375;  // dotted eighth at 120bpm
    delay_fb = 0.35;
    gain_val = 0.6;
    choirSize = 2;

    voiceGroup = Group.new(Crone.server.defaultGroup, \addToHead);
    fxGroup    = Group.after(voiceGroup);

    fxBus    = Bus.audio(Crone.server, 2);
    delayBus = Bus.audio(Crone.server, 2);

    // ── Choir Voice ──────────────────────────────────────────
    // Each voice part (soprano/alto/tenor/bass/lead) is a separate synth
    // with formant-shaped oscillators, vibrato, and breath noise
    SynthDef(\sunday_choir_voice, {
      | out=0, freq=440, amp=0.3, pan=0, gate=1,
        attack=0.08, decay=0.2, sustain=0.85, release=1.2,
        vibRate=5.5, vibDepth=0.004,
        breathAmt=0.02, brightness=0.6,
        formant1=800, formant2=2400, formant3=3200,
        formantQ=0.12, voiceType=0 |

      var sig, env, vib, breath, f1, f2, f3;
      var freqMod, rawSig;

      // Vibrato - human-like with slight randomness
      vib = SinOsc.kr(vibRate + LFNoise1.kr(0.5).range(-0.3, 0.3),
        mul: freq * vibDepth);
      freqMod = freq + vib;

      // Raw voice source - mix of saw (bright) and pulse (hollow) + sub
      rawSig = (Saw.ar(freqMod) * 0.5)
        + (Saw.ar(freqMod * 1.002) * 0.3)  // slight detune for richness
        + (Pulse.ar(freqMod, SinOsc.kr(0.1).range(0.3, 0.5)) * 0.3)
        + (SinOsc.ar(freqMod * 0.5) * 0.15);  // sub octave warmth

      // Formant shaping - three resonant bandpass filters
      // Simulates vowel sounds of choir "aah" / "ooh"
      f1 = BPF.ar(rawSig, formant1, formantQ) * 3;
      f2 = BPF.ar(rawSig, formant2, formantQ * 0.8) * 2;
      f3 = BPF.ar(rawSig, formant3, formantQ * 0.6) * 1;
      sig = (f1 + f2 + f3) * brightness;

      // Add direct signal blended in for presence
      sig = sig + (rawSig * (1 - brightness) * 0.4);

      // Breath noise layer
      breath = BPF.ar(PinkNoise.ar, freqMod * 2, 0.5) * breathAmt;
      breath = breath * EnvGen.kr(Env.perc(0.01, 0.3), gate);
      sig = sig + breath;

      // Low-pass to tame harshness
      sig = LPF.ar(sig, (freq * 8).clip(200, 16000));

      // ADSR envelope with gentle attack for choir feel
      env = EnvGen.kr(
        Env.adsr(attack, decay, sustain, release, curve: -3),
        gate, doneAction: 2);

      sig = Pan2.ar(sig * env * amp, pan);
      Out.ar(out, sig);
    }).add;

    // ── Lead Voice ───────────────────────────────────────────
    // Brighter, more prominent, with wider vibrato for solo/call lines
    SynthDef(\sunday_lead_voice, {
      | out=0, freq=440, amp=0.4, pan=0, gate=1,
        attack=0.03, decay=0.15, sustain=0.9, release=0.8,
        vibRate=6.0, vibDepth=0.006,
        brightness=0.75 |

      var sig, env, vib, freqMod;

      vib = SinOsc.kr(vibRate + LFNoise1.kr(0.8).range(-0.4, 0.4),
        mul: freq * vibDepth);
      freqMod = freq + vib;

      sig = Saw.ar(freqMod) * 0.5
        + Pulse.ar(freqMod, 0.4) * 0.35
        + SinOsc.ar(freqMod) * 0.3;

      // Presence boost at 2-4kHz range
      sig = sig + BPF.ar(sig, 3000, 0.3) * 1.5;
      sig = LPF.ar(sig, (freq * 10).clip(200, 18000));

      env = EnvGen.kr(
        Env.adsr(attack, decay, sustain, release, curve: -2),
        gate, doneAction: 2);

      sig = Pan2.ar(sig * env * amp, pan);
      Out.ar(out, sig);
    }).add;

    // ── Sub Bass Voice ───────────────────────────────────────
    // Deep, warm foundation - hip-hop influenced sub
    SynthDef(\sunday_sub, {
      | out=0, freq=55, amp=0.5, pan=0, gate=1,
        attack=0.02, decay=0.3, sustain=0.8, release=0.6 |

      var sig, env;
      sig = SinOsc.ar(freq) * 0.7
        + SinOsc.ar(freq * 2, mul: 0.2)
        + LPF.ar(Saw.ar(freq), freq * 3) * 0.15;

      sig = sig.tanh;  // gentle saturation

      env = EnvGen.kr(
        Env.adsr(attack, decay, sustain, release),
        gate, doneAction: 2);

      sig = Pan2.ar(sig * env * amp, pan);
      Out.ar(out, sig);
    }).add;

    // ── Lush Cathedral Reverb ────────────────────────────────
    SynthDef(\sunday_reverb, {
      | in=0, out=0, mix=0.45, roomSize=0.9, damp=0.3 |
      var sig, wet;
      sig = In.ar(in, 2);
      wet = FreeVerb2.ar(sig[0], sig[1], 1.0, roomSize, damp);
      // Add subtle shimmer via pitch-shifted reverb tail
      wet = wet + PitchShift.ar(wet * 0.08, 0.2, 2.0, 0, 0.01);
      Out.ar(out, XFade2.ar(sig, wet, mix * 2 - 1));
    }).add;

    // ── Ping-Pong Delay ──────────────────────────────────────
    SynthDef(\sunday_delay, {
      | in=0, out=0, mix=0.15, delTime=0.375, feedback=0.35 |
      var sig, delayed;
      sig = In.ar(in, 2);
      delayed = CombL.ar(sig[0], 2.0, delTime, feedback * 6) * mix;
      // Offset stereo for width
      Out.ar(out, [
        sig[0] + delayed,
        sig[1] + DelayL.ar(delayed, 0.05, 0.023)
      ]);
    }).add;

    Crone.server.sync;

    reverbSynth = Synth(\sunday_reverb,
      [\in, fxBus.index, \out, context.out_b.index,
       \mix, reverb_mix, \roomSize, 0.9, \damp, 0.3],
      target: fxGroup);

    delaySynth = Synth(\sunday_delay,
      [\in, fxBus.index, \out, context.out_b.index,
       \mix, delay_mix, \delTime, delay_time, \feedback, delay_fb],
      target: fxGroup);

    // ── Commands ─────────────────────────────────────────────

    // voiceOn: note, velocity, voiceType, pan
    // voiceType: 0=soprano, 1=alto, 2=tenor, 3=bass, 4=lead, 5=sub
    this.addCommand(\voiceOn, "ifif", { |msg|
      var note = msg[1].asInteger;
      var vel = msg[2].asFloat;
      var vtype = msg[3].asInteger;
      var panVal = msg[4].asFloat;
      var synth, formants;

      // Release existing voice at this note+type combo
      var key = (note * 10) + vtype;
      voices[key] !? { |v| v.set(\gate, 0) };

      case
      { vtype == 4 } {
        // Lead voice
        synth = Synth(\sunday_lead_voice, [
          \out, fxBus.index,
          \freq, note.midicps,
          \amp, vel * gain_val * 0.8,
          \pan, panVal,
          \gate, 1,
          \attack, 0.03, \release, 0.8
        ], target: voiceGroup);
      }
      { vtype == 5 } {
        // Sub bass
        synth = Synth(\sunday_sub, [
          \out, fxBus.index,
          \freq, note.midicps,
          \amp, vel * gain_val * 0.7,
          \pan, 0,  // sub always centered
          \gate, 1
        ], target: voiceGroup);
      }
      {
        // Choir voices (SATB): different formant profiles
        formants = case
          { vtype == 0 } { [\formant1, 900,  \formant2, 2600, \formant3, 3400] }  // soprano "ah"
          { vtype == 1 } { [\formant1, 700,  \formant2, 2100, \formant3, 2800] }  // alto "oh"
          { vtype == 2 } { [\formant1, 600,  \formant2, 1800, \formant3, 2500] }  // tenor "ah"
          { vtype == 3 } { [\formant1, 500,  \formant2, 1400, \formant3, 2200] }; // bass "uh"

        synth = Synth(\sunday_choir_voice, [
          \out, fxBus.index,
          \freq, note.midicps,
          \amp, vel * gain_val * 0.5,
          \pan, panVal,
          \gate, 1,
          \attack, 0.08 + (vtype * 0.02),  // lower voices attack slightly slower
          \release, 1.2 + (vtype * 0.3),
          \vibRate, 5.5 - (vtype * 0.3),
          \breathAmt, 0.02
        ] ++ formants, target: voiceGroup);
      };

      voices[key] = synth;
    });

    this.addCommand(\voiceOff, "ii", { |msg|
      var note = msg[1].asInteger;
      var vtype = msg[2].asInteger;
      var key = (note * 10) + vtype;
      voices[key] !? { |v| v.set(\gate, 0); voices[key] = nil };
    });

    this.addCommand(\allVoicesOff, "", {
      voices.do({ |v| v.set(\gate, 0) });
      voices = Dictionary.new;
    });

    // Reverb controls
    this.addCommand(\reverbMix, "f", { |msg|
      reverb_mix = msg[1].asFloat.clip(0, 1);
      reverbSynth.set(\mix, reverb_mix);
    });
    this.addCommand(\reverbRoom, "f", { |msg|
      reverbSynth.set(\roomSize, msg[1].asFloat.clip(0, 1));
    });

    // Delay controls
    this.addCommand(\delayMix, "f", { |msg|
      delay_mix = msg[1].asFloat.clip(0, 1);
      delaySynth.set(\mix, delay_mix);
    });
    this.addCommand(\delayTime, "f", { |msg|
      delay_time = msg[1].asFloat.clip(0.01, 2.0);
      delaySynth.set(\delTime, delay_time);
    });
    this.addCommand(\delayFeedback, "f", { |msg|
      delay_fb = msg[1].asFloat.clip(0, 0.9);
      delaySynth.set(\feedback, delay_fb);
    });

    // Global gain
    this.addCommand(\gain, "f", { |msg|
      gain_val = msg[1].asFloat.clip(0.0, 1.0);
    });

    // Choir size: affects voice count behavior on Lua side
    this.addCommand(\choirSize, "i", { |msg|
      choirSize = msg[1].asInteger.clip(1, 3);
    });

    // Placeholder commands for compatibility with MollyThePoly parameter system
    this.addCommand(\noteOn,  "if", { |msg|
      var note = msg[1].asInteger;
      var vel = msg[2].asFloat;
      var key = (note * 10) + 4;
      voices[key] !? { |v| v.set(\gate, 0) };
      voices[key] = Synth(\sunday_lead_voice, [
        \out, fxBus.index,
        \freq, note.midicps,
        \amp, vel * gain_val,
        \pan, rrand(-0.3, 0.3),
        \gate, 1
      ], target: voiceGroup);
    });

    this.addCommand(\noteOff, "i", { |msg|
      var note = msg[1].asInteger;
      var key = (note * 10) + 4;
      voices[key] !? { |v| v.set(\gate, 0); voices[key] = nil };
    });

    this.addCommand(\noteKillAll, "", {
      voices.do({ |v| v.set(\gate, 0) });
      voices = Dictionary.new;
    });
  }

  free {
    voices.do({ |v| v !? { v.free } });
    reverbSynth !? { reverbSynth.free };
    delaySynth  !? { delaySynth.free };
    fxBus       !? { fxBus.free };
    delayBus    !? { delayBus.free };
    voiceGroup  !? { voiceGroup.free };
    fxGroup     !? { fxGroup.free };
  }
}
