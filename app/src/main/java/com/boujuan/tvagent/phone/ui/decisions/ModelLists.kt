package com.boujuan.tvagent.phone.ui.decisions

import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.ui.ModelOption

// The models the panel offers (tv-agent's panel/tvpanel/decisions.py and server stt.HOSTED, 2026-10-23): the ones
// benchmarked for this agent. Any other OpenRouter id can still be typed in the picker.

/** The LLM step: OpenRouter chat models that keep to the agent's JSON answers. */
val LLM_MODELS = listOf(
    ModelOption("deepseek/deepseek-v4.1-flash-1017", "DeepSeek V4.1 Flash", R.string.model_note_llm_default),
    ModelOption("google/gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite"),
    ModelOption("google/gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite"),
    ModelOption("google/gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite"),
    ModelOption("reflection/beam-1.0-501b-a23b", "Reflection Beam 1.0"),
    ModelOption("anthropic/claude-haiku-5.5", "Claude Haiku 5.5"),
    ModelOption("openai/gpt-5.6-luna", "GPT-5.6 Luna"),
    ModelOption("z-ai/glm-5.3-flash", "GLM 5.3 Flash"),
)

/** The decision model's hosted one: Liquid's own API ("d1", "d1-ultra") or an OpenRouter decision model. */
val DECISION_MODELS = listOf(
    ModelOption("d1", "d1 (Liquid)", R.string.model_note_d1),
    ModelOption("d1-ultra", "d1-ultra (Liquid)"),
    ModelOption("liquid/d1", "d1 via OpenRouter"),
    ModelOption("liquid/d1-ultra", "d1-ultra via OpenRouter"),
    ModelOption("typesafe/jev-1.14", "JEV 1.14"),
    ModelOption("cloudflare/clef", "Clef"),
    ModelOption("cloudflare/clef-flash", "Clef Flash"),
    ModelOption("nace-ai/drex-v2.0", "Drex v2.0"),
    ModelOption("perplexity/pplx-decider-v1.2", "Perplexity Decider v1.2"),
    ModelOption("inception/mercury-decide", "Mercury Decide"),
)

/** Hosted speech-to-text: OpenRouter transcription models (which use the language hint and the vocabulary). */
val STT_MODELS = listOf(
    ModelOption("assemblyai/universal-3-6-pro", "AssemblyAI Universal-3.6 Pro", R.string.model_note_hint_vocabulary),
    ModelOption("mistralai/voxtral-mini-transcribe-3-0", "Voxtral Mini Transcribe 3", R.string.model_note_hint),
    ModelOption("deepgram/nova-3", "Deepgram Nova-3", R.string.model_note_hint),
    ModelOption("mistralai/voxtral-small-transcribe-3-0", "Voxtral Small Transcribe 3", R.string.model_note_hint),
    ModelOption("x-ai/grok-stt-1.0", "Grok STT 1.0"),
    ModelOption("elevenlabs/scribe-v2", "ElevenLabs Scribe v2", R.string.model_note_hint),
    ModelOption("openai/gpt-4o-mini-transcribe", "GPT-4o mini Transcribe"),
    ModelOption("openai/gpt-transcribe", "GPT Transcribe"),
    ModelOption("microsoft/mai-transcribe-2", "MAI-Transcribe 2"),
    ModelOption("nvidia/parakeet-tdt-0.6b-v3", "Parakeet TDT 0.6B v3 (hosted)"),
    ModelOption("openai/whisper-large-v3-turbo", "Whisper large v3 turbo", R.string.model_note_hint),
)
