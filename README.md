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

Mensagens de progresso e pedidos de aprovação tocam `src/main/resources/sounds/agent_message.mp3`. Quando o agente conclui sua tarefa, toca `src/main/resources/sounds/agent_complete.mp3`, inclusive se o trabalho passar a outro agente. No Android, as notificações usam esses mesmos sons; gere e instale um novo APK para adicioná-los ao aplicativo.

No chat do Windows, clique no microfone para abrir a digitação por voz do sistema com o campo de mensagem em foco. Fale e revise o texto antes de enviá-lo. O Windows precisa de microfone, internet e reconhecimento de fala online habilitado. A digitação usa o idioma de entrada selecionado no Windows.

## Backups protegidos

Em **Configurações → Armazenamento**, use **Exportar backup protegido** e crie uma senha de pelo menos 12 caracteres. O arquivo `.maestrobackup` contém um ZIP cifrado com AES-256-GCM; a senha não é guardada pelo aplicativo. O backup novo inclui `workspace.json` e os arquivos dos projetos, mas exclui sessões do Firebase, chaves de pareamento e acesso local, tokens de notificação e pedidos remotos temporários. Guarde a senha, pois não há recuperação automática.

Para um backup ZIP criado por versões anteriores, use **Proteger ZIP antigo**. O Maestro gera uma cópia `.maestrobackup` sem apagar o ZIP original. Confira que a cópia abre com **Abrir backup protegido** antes de remover o ZIP antigo e suas outras cópias sem senha. Abrir um backup protegido gera um ZIP comum temporário que contém dados privados; remova-o depois do uso. A abertura não restaura dados automaticamente no aplicativo.

## Documentação

- [Arquitetura e direção de evolução](docs/ARQUITETURA.md)
- [Preparação para o GitHub e configurações privadas](docs/GITHUB.md)
- [Guia detalhado de uso do desktop](docs/GUIA_USO.md)
- `maestro-mobile/README.md`: configuração e instalação do aplicativo Android no repositório mobile.

## Verificação

No Windows, `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify.ps1` compila e executa as verificações de fila, agendamento e backup. O código do desktop está em [maestro-desktop](https://github.com/rcastelli1/maestro-desktop); consulte [docs/GITHUB.md](docs/GITHUB.md) antes de enviar alterações ou arquivos locais.
