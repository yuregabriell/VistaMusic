# VistaMusic v0.9.0

Aplicação Android experimental de música com interface premium em **Kotlin, Jetpack Compose e Media3**, sem backend próprio nem base de dados central.

## Novidades
- Pesquisa vazia com **Descobre algo novo**, géneros musicais em cartões e navegação por interesses
- Pesquisa simultânea de artistas, **álbuns reais** e músicas; os álbuns abrem com as suas faixas
- Fotografias e banners de artistas com TheAudioDB; fallback Wikimedia; atribuições nos perfis
- Perfil de artista reorganizado: fotografia, músicas mais populares **quando verificadas pelo TheAudioDB**, álbuns/singles, **Ver todas as músicas**, biografia
- Mini-player com botões **Anterior, Play/Pausa, Próximo** e barra inferior de **progresso e buffer**
- Rankings reais **Apple Music Estados Unidos** e **Apple Music Portugal**, identificados com a sua origem e país, sem confundir rankings com Spotify
- Personalização local **LocalTaste**: mantém até 180 eventos no armazenamento privado da app, pondera artistas, histórico, favoritos, saltos, popularidade relativa, repetição e novidade
- Autoplay/rádio: após uma música isolada ou no fim de uma fila, procura e ordena música relacionada usando dados do dispositivo e os catálogos disponíveis. Não usa um LLM, nem envia histórico para servidor próprio.
- Mantém leitor completo, letras sincronizadas LRCLIB e biblioteca privada.

## Fontes de metadados e reprodução
- Apple RSS Top Charts e iTunes Search API: **metadados, álbuns e charts**, não streaming comercial autorizado
- TheAudioDB: imagens de artistas e rankings, quando disponíveis
- Wikimedia: biografias e imagens públicas
- LRCLIB: letras
- Audius e Jamendo: faixas disponíveis sob condições das fontes
- NewPipeExtractor e fallback experimental Piped (inspirado pelo LibreTube): streaming de música, **não oficial**, sujeito a alterações técnicas, regras de serviços e indisponibilidade. Sem integração com login Spotify.

As recomendações são heurísticas e não equivalem ao Spotify ou a modelos de IA externos. Nem todos os artistas, fotos, álbuns e faixas terão metadados completos. As faixas descobertas nas APIs de catálogo podem não ter um stream reproduzível.

## Build e Release
O GitHub Actions `.github/workflows/android.yml` publica um APK de depuração em `v0.9.0` quando o build passa. A assinatura debug gerada em cada build **não é estável**: instalar versões subsequentes pode exigir desinstalar a anterior e perder preferências locais.

## Licença
GPL-3.0, com dependência GPL-3.0 do NewPipeExtractor. Uso de terceiros sujeito aos respetivos termos e atribuições. Projeto não afiliado a Spotify, Google/YouTube, Apple, Audius ou TheAudioDB.


## Novidades v0.9.0

- YouTube Trending Music, via módulo **YoutubeTrendingMusicExtractor** do NewPipeExtractor, por região PT/US. A secção distingue os YouTube Charts do ranking Apple Music usado como alternativa quando a extração falha. Não é a Home personalizada da conta YouTube Music.
- Perfil de artista com **até 10 faixas**, unindo pesquisa de vídeos do YouTube ordenada por visualizações da amostra, ranking TheAudioDB e discografia iTunes. Não afirmar que as faixas representam o ranking oficial completo da carreira de cada artista.
- Géneros passam a ter **páginas dedicadas** com destaque, faixas de vários artistas, perfis e álbuns — não mudam a caixa de pesquisa para um nome genérico.
- Feito para ti inclui músicas descobertas por pesquisa do YouTube e tendências YouTube, pontuadas localmente por histórico e favoritos.
- Rádio automática tenta utilizar também **relatedItems** do vídeo YouTube que foi reproduzido; continua a depender das fontes não oficiais e dos limites técnicos e de utilização destas.

A experiência ainda não usa autenticação do YouTube Music, portanto não consegue importar o feed pessoal ou as recomendações privadas da conta. Os rankings musicais da plataforma e as sugestões pessoais do VistaMusic são conceitos distintos.

## Revisão v0.9.0
- Home: um único **Em alta agora · YouTube Charts EUA**, sem duplicação de Portugal; se o chart não estiver disponível, não se fazem afirmações falsas sobre rankings.
- O destaque da Home escolhe um tema a partir de sinais **locais** (histórico, favoritos e artistas/géneros relacionados); muda a seleção após reproduções, e usa uma alternativa não personalizada quando ainda não há histórico.
- Mais sons para descobrir: somente faixas obtidas do **YouTube Charts dos EUA**, não da fonte independente Audius. Não é uma lista oficial de lançamentos recentes.
- Capas: para faixas cujo áudio venha de vídeos do YouTube, procurar correspondência de **música + artista** no catálogo público Deezer e apresentar a capa quadrada de álbum quando corresponder. Preservar a miniatura original se a correspondência ou API falharem. A arte é propriedade dos respetivos titulares; verificar termos antes de distribuição pública ou comercial.
- Todos os cartões de músicas, mini-player e player expandido reutilizam um componente de capa com cache e limitação de pedidos.
- A barra inferior no ecrã Explorar fecha corretamente a página do género ao mudar para Home/Pesquisar/Biblioteca.
- O ícone de Definições só aparece ao visitar a Biblioteca.
- O ranking pessoal local pondera também afinidades de género editorial e músicas relacionadas no YouTube (quando disponíveis). Continua a ser uma heurística, não a personalização de uma conta autenticada do YouTube Music.

Os dados, links e fluxos de vídeo associados a integrações não oficiais podem mudar a qualquer momento. A compilação bem-sucedida não garante que todas as chamadas externas ou todos os audios funcionem num dispositivo real.

## Revisão v0.9.0 — capas musicais
- Miniaturas de vídeos do YouTube deixam de ser usadas como arte de músicas, seja na Home, nas listas, no player ou em favoritos antigos.
- Para cada música proveniente do YouTube, o VistaMusic normaliza o nome do artista (por exemplo, sufixos VEVO/Official/Topic) e o título, e procura a capa quadrada do álbum no catálogo Deezer.
- Resultados só são aceites se título e artista corresponderem. O carregamento não exibe a miniatura do vídeo em momento algum.
- Quando a faixa não existe no catálogo, a fonte falha ou a correspondência é incerta, a interface utiliza uma capa tipográfica/gradiente neutra. Nunca apresenta a capa de um álbum diferente só para preencher o campo.
- A procura limita concorrência, memoriza as consultas durante a sessão e não cria um servidor próprio.

O catálogo Deezer é externo e pode impor limites de disponibilidade e utilização da arte. Estas imagens são propriedade dos seus titulares; antes de distribuir o app publicamente ou comercialmente é necessário verificar as permissões e condições aplicáveis.

## v0.9.0 — notificações e ecrã de bloqueio

- ExoPlayer é agora mantido por `PlaybackService : MediaSessionService`, separado da Activity e gerido pelo Android.
- Integração Media3 MediaSession + MediaController com notificação automática no Android e controlos em ecrã bloqueado, notificação e painel de multimédia.
- Metadata: título, artista e artwork do álbum, ou arte gráfica VistaMusic gerada localmente quando o catálogo não apresenta capa correspondente.
- Pausa/retoma, anterior e seguinte por comandos da sessão; suporte a teclas de Bluetooth e auriculares.
- Fila independente no serviço e avanço automático mesmo após sair do app. Ao terminar a fila, o serviço tenta sugestões musicais relacionadas quando Auto DJ está ativo.
- Foco de áudio e pausa ao desligar auriculares. A interação dos controlos e a disposição das notificações depende da versão Android e fabricante.
- A interface usa MediaController em vez de possuir um ExoPlayer que seria libertado ao fechar a Activity.
- Android Foreground Service do tipo `mediaPlayback` com as permissões oficiais requeridas. Notificações multimédia de sessões ativas no Android 13+ são uma categoria isenta da autorização de notificações convencional.

A implantação usa os controlos nativos do Android em vez de replicar a interface e a marca comercial do Spotify. Não há reprodução de conteúdos com DRM contornado; fontes experimentais podem parar de funcionar.
