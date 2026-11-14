# Maestro

Gerenciador local de agentes de IA por projeto. O aplicativo para Windows executa tarefas com o Codex CLI; o aplicativo para celular acompanha conversas e envia comandos ao computador pela rede local ou por uma ponte remota cifrada.

## Projetos de código

| Projeto | Pasta local | Tecnologia |
| --- | --- | --- |
| Desktop e API embutida | `terminal-agentes/` (este repositório) | Java 21, JavaFX, Maven |
| Celular | `maestro-mobile/` (pasta irmã e repositório separado) | React Native, Expo SDK 57, TypeScript |

O mobile foi mantido na pasta separada solicitada para ele. Os dois projetos devem ser versionados juntos quando uma mudança alterar o contrato da API.

## Começar

1. Instale um JDK 21. Para desenvolver no Windows, execute `run.cmd`. O Maven Wrapper baixa as dependências na primeira execução.
2. Instale e autentique o Codex CLI. No Maestro, selecione uma pasta de projeto e configure o executável do Codex se ele não for encontrado automaticamente.
3. Para criar a distribuição Windows com runtime Java, execute `package.cmd`. A saída fica em `target/dist/Maestro-*/Maestro`.
4. Para desenvolver o app do celular, abra a pasta irmã `maestro-mobile/`, instale as dependências com `npm ci` e execute `npm run dev:tunnel` quando o Expo Go não alcançar a rede local.

O computador precisa estar ligado e com o Maestro aberto para que os agentes trabalhem e para que o celular receba respostas.

## Documentação

- [Arquitetura e direção de evolução](docs/ARQUITETURA.md)
- [Preparação para o GitHub e configurações privadas](docs/GITHUB.md)
- [Guia detalhado de uso do desktop](docs/GUIA_USO.md)
- `maestro-mobile/README.md`: configuração e instalação do aplicativo Android no repositório mobile.

## Verificação

No Windows, `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify.ps1` compila e executa as verificações de fila e agendamento. A publicação em GitHub ainda não foi feita; revise [docs/GITHUB.md](docs/GITHUB.md) antes de criar o primeiro commit.
