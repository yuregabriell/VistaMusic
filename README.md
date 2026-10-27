# VistaMusic

App experimental de música para Android com design premium, desenvolvido em **Kotlin + Jetpack Compose + Media3**.

## Funcionalidades v0.4.0
- Pesquisa de músicas populares e independentes com reprodução no player próprio
- Início com destaques musicais, favoritos e artistas seguidos, sem backend nem conta própria
- **Perfis de artistas** com foto, género, músicas, álbuns, singles e texto biográfico atribuído à Wikipédia (quando disponível)
- **Páginas de músicas** com informação de álbum, ano, género e letra quando disponível
- **Páginas de álbuns** com capa, ano e lista de faixas
- Player expandido com capa, barra de progresso, anterior, seguinte, aleatório, repetição e favoritos
- Letras sincronizadas ou sem sincronização via **LRCLIB** (gratuito, sem chave); destaque e scroll automático
- Barra de estado respeitada com padding em todas as páginas
- Sem WebView do YouTube, sem modal de preparação, sem texto da fonte no catálogo, sem indicador linear na pesquisa

## Reprodutores e metadados
- NewPipeExtractor: resolução experimental de streams de áudio para Media3
- **Fallback inspirado no LibreTube**: consulta aos servidores **Piped** com endpoint documentado `/streams/:videoId`, usando apenas audioStreams. **Não foi copiado nem incorporado o aplicativo LibreTube completo**.
- Audius: streaming de música independente
- Jamendo: integração opcional com Client ID
- Apple iTunes Search API: informação de artistas, músicas, álbuns e prévias quando ativadas
- Wikimedia REST API: biografia e imagem do artista
- LRCLIB: letra LRC com timestamps e letra não sincronizada

Nenhum destes conectores constitui acesso oficial e autorizado ao catálogo integral do Spotify ou do YouTube Music. Serviços experimentais podem ser interrompidos por alterações externas, bloqueios ou políticas dos fornecedores. Os termos de cada serviço permanecem aplicáveis. A disponibilização de metadados não garante a reprodução de todas as músicas.

## Compilação e APK
A GitHub Action `.github/workflows/android.yml` compila `assembleDebug` e, após êxito, publica o APK na prerelease `v0.4.0`.

Versão de testes assinada com chave debug efémera do GitHub Actions. Para atualizações sem desinstalação será necessária uma chave de assinatura estável.

## Licenças e atribuições
Código licenciado sob GPL-3.0. NewPipeExtractor e LibreTube são software GPL, mas o VistaMusic não inclui código-fonte do LibreTube. Piped e LRCLIB são fornecedores externos. Biografias da Wikipédia sujeitas às respetivas licenças de conteúdo. O VistaMusic não é afiliado ao Spotify, Google, YouTube, Apple ou Wikimedia.
