Eye Tracking & Face Detection App
📋 Descrição

Aplicativo Android desenvolvido em Kotlin com Compose para detecção facial em tempo real e rastreamento ocular usando Machine Learning. O sistema detecta movimentos dos olhos e exibe uma interface visual interativa.
🚀 Funcionalidades Principais
Detecção Facial Avançada

    Reconhecimento de rostos em tempo real

    Detecção de landmarks faciais (olhos, nariz, boca)

    Contornos faciais precisos

    Classificação de expressões faciais

Rastreamento Ocular

    Detecção de movimento dos olhos (esquerda, direita, cima, baixo)

    Identificação de piscadas

    Posicionamento em tempo real do ponteiro ocular

    Modelo TensorFlow Lite para inferência

Interface Visual

    Overlay gráfico em tempo real

    Ponteiro ocular que segue o movimento

    Contornos faciais e landmarks

    Alternância entre câmeras frontal e traseira

🛠️ Tecnologias Utilizadas
Framework Principal

    Android Jetpack Compose - UI moderna declarativa

    CameraX - Captura e processamento de vídeo

    ML Kit Face Detection - Detecção facial do Google

Machine Learning

    TensorFlow Lite - Modelo para detecção ocular

    Google ML Kit - APIs de visão computacional

    Processamento em tempo real - Análise de frames

Arquitetura

    Compose UI - Interface declarativa

    Image Analysis - Processamento de imagens

    View System - Overlay customizado

📱 Características Técnicas
Processamento de Imagem

    Análise de frames da câmera em tempo real

    Detecção de múltiplos rostos

    Extração de landmarks faciais

    Cálculo de posições relativas dos olhos



🔧 Configuração e Permissões
Permissões Requeridas
xml

<uses-permission android:name="android.permission.CAMERA" />

Dependências Principais

    androidx.camera:camera-core

    androidx.camera:camera-lifecycle

    com.google.mlkit:face-detection

    org.tensorflow:tensorflow-lite

📊 Estrutura do Projeto
Componentes Principais

    MainActivity - Activity principal com Compose

    CameraScreen - Tela da câmera com detecção

    FaceGraphicOverlay - View customizada para overlay

    EyeMovementDetector - Detector de movimento ocular

    EyePointerOverlay - Composable do ponteiro ocular

Fluxo de Dados

    Captura → Câmera obtém frames

    Análise → ML Kit detecta faces

    Processamento → TensorFlow analisa olhos

    UI Update → Interface atualizada em tempo real

🎨 Features de Interface

    Preview da Câmera - Visualização em tempo real

    Overlay Gráfico - Desenhos sobre a câmera

    Ponteiro Interativo - Feedback visual do movimento ocular

    Botão de Alternância - Troca entre câmeras frontal/traseira
