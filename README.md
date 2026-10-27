# VistaMusic

Um leitor musical Android independente em **Kotlin, Jetpack Compose e Media3**, sem servidor próprio.

## Fontes

- **YouTube (experimental):** pesquisa musical e extração de links de áudio com [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), com reprodução no player nativo (sem abrir YouTube nem carregar anúncios do YouTube dentro do VistaMusic). A integração é não oficial e pode falhar quando o serviço altera a sua infraestrutura. Não contorna DRM.
- **Audius:** músicas completas onde a API permite transmissão.
- **Jamendo:** fonte opcional com Client ID.
- **iTunes/Apple:** dados do catálogo, listas populares e prévias opcionais de 30 segundos.

## Atenção

A reprodução com NewPipe pode não funcionar com determinados vídeos, países ou redes. Não existe garantia de catálogo completo ou disponibilidade contínua sem anúncios. O YouTube mantém as suas próprias regras de utilização e distribuição de conteúdo.

## Compilação

O GitHub Actions constrói um APK de testes a cada atualização da branch principal e publica uma prerelease (`v0.3.0`) quando a compilação passa.

## Licença

GPL-3.0. Inclui a biblioteca GPL-3.0 NewPipe Extractor como dependência; mantém-se a atribuição e código-fonte público. Não é afiliado ao YouTube, Google, Apple, Spotify ou Audius.
