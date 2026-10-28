# VistaMusic para iPhone — v0.1.0 (prévia)

Port iOS nativo em **SwiftUI**, integrado no mesmo repositório do VistaMusic Android. O Android permanece independente em app/.

## O que funciona nesta primeira base
- Início premium, pesquisa, capas, álbuns e faixas por álbum; pesquisa iTunes por nome de música, artista ou álbum.
- Ranking Apple Music Portugal e EUA, identificado como catálogo/metadata (não reproduz faixas completas através da API pública).
- Audius: pesquisa, faixas em alta e reprodução de streams completos disponibilizados pelo serviço.
- iTunes Search: prévias oficiais de cerca de 30 segundos, quando disponíveis.
- Biblioteca privada no iPhone: curtidas, playlists próprias, adicionar/remover faixas, histórico e descoberta simples baseada no gosto local.
- AVPlayer, reprodução em segundo plano, MediaPlayer/Now Playing, comandos do ecrã bloqueado e Centro de Controlo.

## Diferenças em relação ao Android
- Sem YouTube Music/NewPipeExtractor nesta versão. É uma integração Android não portátil diretamente para iOS. O catálogo Spotify/YouTube não pode ser apresentado como se houvesse streaming oficial licenciado.
- Sem sincronização Vista ID ainda. Favoritos e playlists iOS guardam-se no UserDefaults e não importam automaticamente os dados Android.
- Sem letras sincronizadas ou perfis aprofundados de artistas nesta prévia.
- O catálogo Audius varia por região e disponibilidade; a reprodução pode falhar por restrições do fornecedor.
- As capas, faixas e metadados de terceiros pertencem aos respetivos titulares e estão sujeitos a condições de utilização.

## Gerar a app em macOS
Requer Xcode e XcodeGen.

    cd ios
    xcodegen generate
    open VistaMusic.xcodeproj

Para instalar num iPhone pelo Xcode é preciso selecionar o seu Apple Team/Apple ID em Signing & Capabilities e um Bundle ID exclusivo, compilando com uma assinatura e perfil de aprovisionamento válidos. A Apple também exige essa assinatura para builds instaláveis diretamente em dispositivos.

## GitHub Releases
O workflow .github/workflows/ios.yml compila no macOS e publica um pacote **IPA sem assinatura**. Serve para re-assinar localmente com uma ferramenta compatível (por exemplo, SideStore) ou como artefacto de teste; não é um IPA pronto para instalação direta por Safari. Sem chaves de assinatura na CI, não é possível publicar um IPA já instalável em qualquer iPhone.

Este projeto é experimental e não é afiliado à Apple, Spotify, YouTube, Audius ou Jamendo.
