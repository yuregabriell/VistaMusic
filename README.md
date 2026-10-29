# VistaMusic Web — instalação 100% gratuita no iPhone, sem cabo nem computador

Esta é uma **versão web instalável (PWA)**, diferente da app iOS nativa em SwiftUI.
Não necessita de Apple Developer, assinatura Apple, Mac ou certificado. O site é
hospedado gratuitamente pelo GitHub Pages.

## Publicar GitHub Pages (uma única vez)

No repositório `yuregabriell/VistaMusic`, o proprietário entra em
**Settings → Pages → Build and deployment → Source: Deploy from a branch**,
escolhe **Branch: gh-pages**, pasta **/(root)**, e clica em **Save**.

Após a publicação, endereço: **https://yuregabriell.github.io/VistaMusic/**.

No iPhone: abrir o endereço em **Safari → Partilhar → Adicionar ao ecrã principal**
e manter **Abrir como app web** selecionado. O ícone VistaMusic aparece na Home,
sem cabo, assinatura, pagamento ou renovação a cada 7 dias.

## O que está implementado
- Design VistaMusic: rosa `#FF4FA3`, fundo escuro, Home e recomendações.
- Audius: catálogo de tendências e pesquisa, streaming de músicas disponíveis
  sem conta, com controlos de áudio, mini-player e player expandido.
- Biblioteca local por dispositivo: favoritos, playlists próprias, histórico.
- MediaSession para comandos de multimédia quando suportados no iOS.
- Letras do LRCLIB quando disponíveis.
- YouTube: abrir link para vídeo no **player oficial com imagem e anúncios**
  (não existe extração de áudio YouTube nem reprodução de fundo).
- Service Worker para interface básica sem ligação; as músicas necessitam de rede.

## Limitações
- **Não é o IPA nativo** e **não é funcionalmente idêntico ao Android**.
- O Safari limita algumas funções em segundo plano e dependem do iOS.
- Não há integração NewPipeExtractor/YouTubeKit/InnerTube ou YouTube Music
  com reprodução áudio em fundo. A pesquisa do YouTube Music abre a plataforma.
- Audius, LRCLIB e YouTube podem ter disponibilidade e conteúdo variáveis.
- Não sincroniza com Vista ID; dados ficam na memória local do Safari.
- Sem publicidade própria; vídeos YouTube podem ter publicidade da plataforma.
- O site só se torna acessível depois de ativar o GitHub Pages no repositório.

O projeto não representa Apple, YouTube, Audius ou LRCLIB.
