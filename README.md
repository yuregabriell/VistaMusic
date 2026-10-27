# VistaMusic v0.5.0

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
O GitHub Actions `.github/workflows/android.yml` publica um APK de depuração em `v0.5.0` quando o build passa. A assinatura debug gerada em cada build **não é estável**: instalar versões subsequentes pode exigir desinstalar a anterior e perder preferências locais.

## Licença
GPL-3.0, com dependência GPL-3.0 do NewPipeExtractor. Uso de terceiros sujeito aos respetivos termos e atribuições. Projeto não afiliado a Spotify, Google/YouTube, Apple, Audius ou TheAudioDB.
