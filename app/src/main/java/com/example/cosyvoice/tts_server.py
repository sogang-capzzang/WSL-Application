# # tts_server.py
# from flask import Flask, Response
# import os
# import torch
# import torchaudio
# from cozyvoice import CozyVoice  # CozyVoice 모듈 (가정)
#
# app = Flask(__name__)
# cozy_voice = CozyVoice()  # CozyVoice 모델 초기화 (40GB 모델 로드)
#
# @app.route('/tts', methods=['POST'])
# def generate_tts():
#     text = request.form['text']
#     voice_file = request.files['voice_file']
#
#     # 임시 디렉토리 생성
#     temp_dir = "tts_output"
#     os.makedirs(temp_dir, exist_ok=True)
#     voice_path = os.path.join(temp_dir, voice_file.filename)
#     voice_file.save(voice_path)
#
#     # CozyVoice로 TTS 생성 (사용자 목소리 기반)
#     output_wav_files = cozy_voice.generate(text, voice_path)  # 이미 분리된 WAV 파일 리스트 반환
#
#     def generate():
#         for i, wav_file in enumerate(output_wav_files, 1):
#             with open(wav_file, 'rb') as f:
#                 # WAV 파일 데이터를 청크 단위로 스트리밍
#                 while True:
#                     chunk = f.read(1024)
#                     if not chunk:
#                         break
#                     yield chunk
#
#     return Response(generate(), mimetype='audio/wav')
#
# if __name__ == "__main__":
#     app.run(host='0.0.0.0', port=8080)