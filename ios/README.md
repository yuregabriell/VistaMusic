# VistaMusic para iPhone — v0.2.0 (experimental)

Port **SwiftUI nativo** do VistaMusic Android, sem modificar a pasta Android `app/`. O projeto usa XcodeGen para gerar o Xcode project.

## Paridade com o Android
- Identidade visual de fundo escuro #090B0E, superfície #191D21 e rosa VistaMusic #FF4FA3.
- Home com destaque personalizável, descoberta, secções de músicas, mini-player, player expandido e pesquisa.
- **YouTube Music Songs:** pesquisa não autenticada via WEB_REMIX/InnerTube, adaptada do módulo `YouTubeMusicSongs.kt` do Android; prioridade na pesquisa e descoberta.
- **YouTubeKit (Swift):** resolução experimental de áudio para AVPlayer; cumpre a função técnica que NewPipeExtractor executa no Android, mas não usa a mesma biblioteca ou código Android.
- Playlists, músicas curtidas, histórico, biblioteca, procura por géneros e página de artista com discografia por ano.
- Reprodução com AVPlayer, comandos do ecrã bloqueado/Centro de Controlo e segundo plano para streams compatíveis.
- Letras do LRCLIB, sincronizadas quando disponíveis.
- Audius (faixas completas) e iTunes (metadados, álbuns e prévias) como fontes alternativas.
- Rankings Apple Music PT/EUA são metadados; a secção YouTube Music é **pesquisa de músicas em destaque**, não um ranking oficial da plataforma.

## Limitações técnicas e de distribuição
- Integração YouTube Music/YouTube **não oficial**: pode falhar por mudanças do serviço, contas, regiões, bloqueios de streams, termos de utilização ou disponibilidade. Nenhuma taxa de sucesso é garantida sem testes reais.
- A política do YouTube para a API oficial restringe separar áudio, remover publicidade e reprodução em segundo plano com o player incorporado. Este projeto é **experimental**; o uso e eventual publicação pública exigem avaliar direitos e condições de cada fornecedor.
- Isto não implementa autenticação YouTube, Spotify nem a Home personalizada da conta YouTube Music.
- Vista ID e sincronização cloud entre Android e iOS ainda não implementados. Os dados são privados e separados em cada dispositivo.
- Letras, capas e metadados são de terceiros, sujeitos às suas licenças e termos.
- A interface é adaptada a iOS e acompanha a arquitetura visual do Android, mas não está certificada como pixel-perfect nem testada num iPhone real.

## Gerar e instalar

Requer Xcode 16, macOS e XcodeGen:

```bash
cd ios
xcodegen generate
open VistaMusic.xcodeproj
```

Seleciona a tua Apple Team, cria um Bundle ID exclusivo e assina antes de instalar no dispositivo. Na CI, `.github/workflows/ios.yml` compila em macOS e, se todos os passos passarem, disponibiliza um **IPA não assinado** no GitHub Releases. Este ficheiro não é instalável diretamente pelo Safari; precisa de assinatura Apple e distribuição com AltStore/SideStore ou Xcode, consoante o método de instalação.

O projeto não é afiliado a YouTube/Google, Apple, Spotify, Audius, LRCLIB ou aos outros fornecedores. Código experimental sem qualquer garantia de reprodução de conteúdo protegido.
