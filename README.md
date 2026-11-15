# Maestro

**Um lugar para organizar agentes de IA e acompanhar o trabalho deles em projetos reais.** O Maestro reúne conversas, tarefas, arquivos, conhecimento e ferramentas em uma interface de chat. Você escolhe a pasta do projeto, cria agentes com funções próprias e acompanha o que cada um está fazendo. O trabalho com IA é executado no seu computador pelo Codex CLI.

O aplicativo desktop foi desenvolvido para **Windows**. O aplicativo mobile foi desenvolvido para **Android** e permite conversar com os agentes, acompanhar as tarefas e aprovar ações pelo celular, inclusive fora de casa. O suporte a **Linux, macOS e iPhone** está nos planos futuros.

O desktop deste repositório usa **Java 21, JavaFX e Maven**. O projeto Android, mantido separadamente na pasta irmã `maestro-mobile/`, usa **React Native, Expo SDK 57 e TypeScript**.

## Funcionalidades atuais

### Projetos, agentes e conversas

- **Projetos:** crie espaços separados, associe cada um a uma pasta no computador e acompanhe a atividade na visão geral.
- **Agentes personalizados:** crie, edite e exclua agentes; defina nome, função, instruções, modelo, acesso ao conhecimento e permissão para editar arquivos. Escolha também personagem, cores e animações.
- **Chats em grupo:** mantenha várias conversas por projeto. Use `@` para escolher um agente; a lista de menções filtra os nomes enquanto você digita. Os agentes recebem o contexto recente da conversa em que trabalham.
- **Chats individuais:** converse com um agente sem acionar os outros. Cada conversa individual mantém seu próprio histórico e sua sessão do Codex.
- **Mensagens e anexos:** envie texto e arquivos, cole capturas de tela com `Ctrl+V`, visualize imagens no chat, copie mensagens e responda citando uma mensagem anterior.
- **Encaminhamento entre agentes:** um agente pode passar etapas a outros agentes mencionados em sua resposta. O Maestro preserva o contexto da tarefa e inicia as etapas em sequência.
- **Fila compartilhada:** mensagens enviadas enquanto um agente está ocupado entram em uma fila do projeto. Você pode mudar a prioridade, editar ou apagar suas mensagens ainda não iniciadas. Envios do Windows e do Android usam a mesma fila.
- **Mensagens agendadas:** programe texto e anexos para uma data e hora, para o próximo reset de 5 horas ou para o reset semanal do Codex. Agendamentos vencidos são processados quando o Maestro volta a abrir.
- **Transcrição de voz:** dite uma mensagem pelo microfone no Windows ou no Android, veja o texto aparecer no campo e revise antes de enviar.

### Trabalho no projeto

- **Tarefas e aprovações:** acompanhe andamento, mensagens de progresso e pedidos de decisão. Interrompa uma tarefa, pause a fila do agente ou passe para a próxima tarefa.
- **Permissões de arquivos:** configure a escrita como **Permitir**, **Aprovar** ou **Bloquear**. A opção **Aprovar** pede sua confirmação antes de iniciar uma tarefa que pode alterar arquivos; as restrições efetivas também são aplicadas pelo Codex CLI.
- **Arquivos e Git:** explore arquivos, examine alterações e diffs, prepare ou reverta mudanças e faça commits pelo desktop.
- **Terminais integrados:** abra várias abas de terminal, iniciadas na pasta do projeto selecionado.
- **Base de conhecimento (RAG):** importe arquivos e pastas, indexe o texto localmente, pesquise trechos e escolha quais agentes podem consultá-los. A busca atual é textual; PDFs digitalizados precisam de OCR externo.
- **MCPs e plugins:** configure servidores MCP por projeto e gerencie plugins do Codex CLI instalados no computador.
- **Modelos e uso do Codex:** escolha o modelo GPT e o nível de raciocínio de cada agente, confira a conexão do Codex e acompanhe os limites de uso, horários de reset e créditos de reset quando essas informações estiverem disponíveis.

### Interface e celular

- **Personagens animados:** os agentes aparecem nos cartões, nas tarefas e no chat. O mini player do Windows acompanha a atividade quando a janela perde o foco.
- **Notificações:** o Android pode avisar sobre mensagens dos agentes com nome, prévia, imagem do agente e sons de andamento ou conclusão. As notificações exigem um APK instalado e a configuração de push descrita no projeto mobile.
- **Acesso remoto:** pelo Android, você pode abrir os projetos, conversar em grupo ou individualmente, enviar anexos, acompanhar tarefas e responder a aprovações. O computador continua executando os agentes.
- **Dados e backups:** os projetos e o histórico ficam no computador. O Maestro também oferece exportação de backup protegido por senha.

## Como o celular se conecta

Para usar o Maestro **de qualquer lugar**, você configura **seu próprio projeto Firebase**. O celular envia os pedidos por uma ponte remota; o Maestro aberto no computador recebe esses pedidos, executa o trabalho e devolve as atualizações. O conteúdo trocado por essa ponte é cifrado antes de ir ao Firestore. A conta Firebase e a chave remota do Maestro são necessárias para conectar o celular. Não é preciso abrir uma porta no roteador.

As notificações push são uma parte separada: suas prévias passam pelos serviços de notificação da Expo e do Google.

O **computador precisa estar ligado, com o Maestro aberto e conectado à internet** para que os agentes trabalhem e o celular receba as respostas. Se você quiser usar apenas o aplicativo de Windows, **não precisa de Firebase**.

Também existe um modo de conexão pela rede local. Ele usa HTTP e uma chave de acesso, portanto deve ser usado somente em uma rede em que você confie. Para acesso fora da rede local, use a opção **De qualquer lugar** com Firebase.

## Requisitos

| Uso | Requisitos |
| --- | --- |
| Desktop a partir do código | Windows, JDK 21, internet para baixar dependências na primeira execução e Codex CLI instalado e autenticado. O repositório inclui o Maven Wrapper. |
| Desktop empacotado | Windows e Codex CLI instalado e autenticado. A distribuição gerada por `package.cmd` inclui o runtime Java. |
| Android | Aparelho Android, código do projeto `maestro-mobile`, Node.js 22 e npm para desenvolver ou gerar o APK. Para o APK, é necessária uma conta Expo/EAS. |
| Acesso remoto pelo celular | Projeto Firebase próprio com Firestore e Authentication, conta de usuário, configuração do app mobile e Maestro desktop aberto em um computador com internet. |

Para ditar mensagens, é preciso autorizar o microfone. No Windows, a função usa o reconhecimento de fala do sistema, que precisa estar habilitado e conectado à internet. No Android, o reconhecimento de fala e as notificações exigem um APK instalado com os módulos nativos correspondentes.

## Colocar o desktop para funcionar

1. Instale o **JDK 21** e o **Codex CLI** no Windows. Autentique o CLI com uma conta que tenha acesso ao Codex.
2. Clone este repositório ou baixe seu código. Abra um terminal na pasta que contém `run.cmd`.
3. Configure `JAVA_HOME` para apontar para o JDK 21 se ele não estiver configurado. Execute:

   ```
   .\run.cmd
   ```

4. No Maestro, crie um projeto e selecione a pasta em que os agentes trabalharão. Em **Modelos**, confira a conexão do Codex. Se o executável não for encontrado, use **Localizar Codex CLI**.
5. Crie ou edite seus agentes, escolha os modelos e comece uma conversa individual ou em grupo.

Para gerar uma versão Windows com executável, configure `JAVA_HOME` e execute `.\package.cmd`. O programa será criado em `target/dist/Maestro-*/Maestro/Maestro.exe`. Mantenha toda a pasta `Maestro` junto ao executável. O script também atualiza um atalho existente na Área de Trabalho quando ele aponta para uma distribuição anterior criada pelo próprio projeto.

## Colocar o Android para funcionar

O código mobile fica na pasta irmã `maestro-mobile/`, em um projeto separado. Configure o Firebase antes de gerar o APK para acesso remoto:

1. No **Firebase Console**, crie um projeto seu. Ative **Cloud Firestore** em modo de produção e **Authentication → Email/Password**. Crie o usuário que vai usar o Maestro e anote o **UID** dele.
2. Registre um **app Web** no projeto Firebase. No projeto mobile, copie `src/firebaseConfig.example.ts` para `src/firebaseConfig.local.ts`, preencha os identificadores exibidos pelo Firebase e informe o UID em `ownerUid`. Não coloque senhas nesse arquivo.
3. Copie [`firebase/firestore.rules.example`](firebase/firestore.rules.example) para `firebase/firestore.rules` neste repositório, substitua `COLOQUE_SEU_UID_AQUI` pelo UID criado e publique o conteúdo em **Firestore Database → Rules**. Mantenha as regras restritas ao seu usuário.
4. Registre um **app Android** no mesmo projeto Firebase usando o identificador definido em `maestro-mobile/app.json`. Baixe `google-services.json` e coloque-o na raiz de `maestro-mobile/`.
5. Abra o Maestro no Windows e acesse **Configurações → Celular e acesso remoto → Acesso de qualquer lugar**. Se necessário, selecione o arquivo `maestro-mobile/src/firebaseConfig.local.ts`. Entre com o usuário criado no Firebase e clique em **Verificar conexão**. Copie a **chave remota** exibida nessa tela.
6. Na pasta `maestro-mobile`, instale as dependências e gere um APK:

   ```
   npm ci
   npx.cmd eas-cli@latest login
   npx.cmd eas-cli@latest build -p android --profile preview
   ```

7. Instale o APK no Android. No app, escolha **De qualquer lugar**, entre com o mesmo usuário Firebase e informe a chave remota do Maestro. Faça um teste enviando uma mensagem com o computador ligado e o Maestro aberto.

Cada pessoa que for gerar seu próprio APK deve vincular `maestro-mobile/app.json` ao seu projeto Expo/EAS. Para receber **notificações push** no Android, configure também as credenciais **FCM v1** no EAS. O passo a passo completo está no README da pasta `maestro-mobile/`. Para testar as telas no Expo Go durante o desenvolvimento, use `npm run dev:tunnel`; o APK instalado funciona sem o servidor de desenvolvimento.

Os arquivos `firebaseConfig.local.ts` e `google-services.json` ficam fora do Git. Guarde senhas, chaves remotas e chaves privadas de conta de serviço fora do repositório.

## Backups protegidos

Em **Configurações → Armazenamento**, use **Exportar backup protegido** para criar um arquivo `.maestrobackup` cifrado e protegido por uma senha definida por você. O backup inclui os dados do projeto e exclui sessões e chaves de acesso remoto. Guarde a senha: o Maestro não a armazena nem consegue recuperá-la. O [guia de uso](docs/GUIA_USO.md) explica como proteger backups ZIP antigos e abrir um backup protegido.

## Mais informações

- [Guia detalhado de uso](docs/GUIA_USO.md)
- [Arquitetura do código](docs/ARQUITETURA.md)
- O aplicativo Android tem um README próprio na pasta irmã `maestro-mobile/`.
