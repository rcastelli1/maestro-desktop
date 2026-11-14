# maestro

## Mensagens agendadas

No chat, escreva a mensagem e clique em **◷** (desktop) ou no ícone de calendário com relógio (celular). Escolha **dia e horário**, **reset de 5 horas** ou **reset semanal**, e confirme. Abra **Agendadas** no cabeçalho do chat desktop ou o mesmo ícone no celular para ver e cancelar mensagens pendentes. O agendamento usa a conversa e o agente selecionados. Você pode anexar até quatro arquivos, somando no máximo 8 MB, inclusive sem texto. Os arquivos ficam guardados localmente até o envio ou cancelamento.

O horário de reset vem do Codex CLI conectado no computador. Se o Codex não informar esse horário, a mensagem aguarda e o Maestro tenta consultar novamente. O computador precisa estar ligado e com o Maestro aberto no momento do envio; mensagens vencidas são enviadas quando o aplicativo for reaberto. Mensagens cujo projeto ou Codex estejam temporariamente indisponíveis continuam pendentes para nova tentativa.

## Fila de mensagens

Quando um agente está ocupado, novas mensagens para ele entram na **Fila do projeto**, exibida acima do campo de chat no desktop e no celular. Cada item mostra o agente e a conversa de origem. Use as setas para mudar a prioridade entre mensagens do mesmo agente; antes de começar a execução, também é possível editar o texto ou apagar uma mensagem enviada por você. A fila é salva no computador e compartilhada pelos dois aplicativos. Mensagens enviadas de qualquer um deles seguem a mesma ordem; tarefas já em andamento não podem ser editadas pela fila.

Aplicativo **desktop para Windows**, feito em Java 21 e JavaFX, com aplicativo **mobile em React Native/Expo**. O computador guarda os dados e executa as tarefas; o celular acompanha o projeto pela rede local ou, após pareamento, de qualquer lugar pela internet.

## Abrir o aplicativo

Abra o atalho **Maestro** na Área de Trabalho. A pasta do executável já inclui o runtime Java; mantenha seu conteúdo junto ao `.exe`. O Codex CLI precisa estar instalado e autenticado para executar tarefas com IA.

Para rodar a partir do código, use `run.cmd` com um JDK 21 instalado. Para gerar novamente o executável, use `package.cmd`. Ele cria uma versão em `target/dist/Maestro-*/Maestro`, mostra o caminho completo e atualiza o atalho Maestro existente na Área de Trabalho. A versão em execução pode continuar aberta. Na primeira compilação, o Maven baixa as dependências.

## Telas e recursos

**Mini player:** quando a janela principal perde o foco ou é minimizada, aparece a cápsula de 520 × 92 px do protótipo, com personagem, atividade, indicadores de agentes e botão circular. Ela alterna entre os agentes com tarefas ativas; quando não há trabalho, mostra o estado de espera. Dê dois cliques na cápsula para voltar ao Maestro. O botão quadrado interrompe a tarefa ativa e pausa a fila daquele agente; o botão de reprodução retoma uma fila pausada ou abre o Maestro. A cápsula pode ser arrastada. Ela permanece no computador e não é o aplicativo para celular.

**Visuais dos agentes:** ao criar ou editar um agente, escolha um entre dez personagens e dez cores prontas ou digite uma cor personalizada em hexadecimal (`#RRGGBB`). A cor escolhida pinta o corpo, os braços e os detalhes do personagem nos cartões, no chat, nas tarefas e no mini player. Os braços ficam ocultos em repouso; durante o aceno, o braço sai do corpo, faz o gesto e volta para trás da silhueta antes de sumir. A cor dos olhos também aceita um hexadecimal próprio; os olhos são cápsulas verticais, sem brilho branco. O editor mostra uma amostra das duas cores e atualiza os personagens quando o código está completo. Os personagens flutuam continuamente, inclusive nos avatares do chat. Durante o trabalho, o ciclo inclui leitura com óculos e livro, digitação diante do monitor, café com fumaça, aceno e espirro; o sono ocorre apenas quando o agente está disponível ou descansando no mini player. No modal de criação e edição, oito caixas permitem ativar ou desativar individualmente a flutuação, o piscar, o pulso da antena do Orbe, o aceno, o espirro, o sono, a leitura e a cena do computador com café. Essas escolhas são salvas por agente e usadas também no chat e no mini player. No ciclo de sono, o corpo respira e a bolha expande e contrai por cerca de 20 segundos. Depois do estouro, o agente acorda assustado, olha para a direita por um segundo, para a esquerda por um segundo e fecha os olhos gradualmente. A opção de reduzir animações nas Configurações desliga os movimentos; os acessórios da leitura permanecem visíveis nos agentes ocupados.

Orbe, Gota e Cubo preservam as formas e medidas dos SVGs fornecidos. Os outros sete seguem o mesmo desenho com a cor escolhida para cada agente. O mini player inclui as fontes Sora e JetBrains Mono em `src/main/resources/fonts`, junto das respectivas licenças OFL.

- **Projetos e visão geral:** cria projetos, vincula a pasta de trabalho e mostra a atividade.
- **Chat:** cria e renomeia várias conversas em grupo por projeto; o histórico anterior vira a conversa **Chat principal**. A seção **Individuais** abre uma conversa exclusiva com cada agente, também acessível pelo botão **Conversar** no cartão do agente. A conversa individual mantém histórico, rascunho e sessão do Codex próprios; as mensagens vão sempre para o agente escolhido, mesmo se contiverem `@Nome`, e não acionam os demais. No grupo, digitar `@` abre uma lista de agentes acima do campo; o nome digitado filtra as opções, com indicação quando não há resultado. Use as setas e Enter/Tab ou clique para inserir a menção completa. A primeira menção completa escolhe o destinatário, inclusive quando há nomes parecidos. Cada conversa tem anexos pendentes próprios e aceita texto, PDF, DOCX e imagens. Capturas de tela podem ser coladas com **Ctrl+V** no chat; imagens anexadas aparecem em miniatura antes do envio e dentro da conversa depois de enviadas. Um clique abre a imagem maior. Também é possível enviar somente o anexo, sem texto. Ao iniciar cada tarefa, o agente recebe um retrato da conversa selecionada, com autor, destinatário, respostas, decisões e estado das tarefas naquela conversa. O histórico recente enviado tem limite de 24 mil caracteres; mensagens mais antigas podem ser omitidas. A conversa acompanha a parte inferior da tela, sobe suavemente quando chegam mensagens e mostra um indicador animado enquanto o agente trabalha. Atualizações intermediárias que o Codex disponibilizar aparecem como mensagens do agente, com a atividade recente em uma etiqueta discreta; aprovações e perguntas ficam na própria conversa. O texto das mensagens pode ser selecionado com o mouse e copiado com Ctrl+C; o menu do botão direito permite copiar qualquer mensagem e também copiar uma imagem anexada como imagem. O botão ↩ ou o menu **Responder** abre uma citação acima do campo, que pode ser cancelada antes do envio e aparece na mensagem enviada. A resposta final aparece separadamente. O raciocínio interno não é exibido.
- **Encaminhamento entre agentes:** quando um agente cita vários `@Nomes` na resposta final, o Maestro cria uma tarefa para o primeiro e guarda os demais em sequência, iniciando cada etapa após a conclusão da anterior. Cada agente recebe o plano original, o resultado recente e o contexto do grupo; se estiver ocupado, a tarefa fica na fila. Tarefas com permissão de escrita continuam pedindo aprovação quando essa for a regra do projeto. Respostas antigas com menções exibem **Encaminhar para @Nome** para iniciar manualmente o trabalho que ficou apenas no texto. A sequência automática para após seis encaminhamentos, com opção de continuar manualmente.
- **Agentes:** cria e edita papel, instruções, modelo, acesso à base de conhecimento e permissão de edição.
- **Tarefas e arquivos:** acompanha tarefas, atividade e aprovações; mostra perguntas com opções quando um agente precisa de uma decisão. Mensagens enviadas a um agente ocupado entram na fila, inclusive com seus anexos. Clique numa tarefa em andamento para escolher **Parar** (interrompe e pausa a fila desse agente) ou **Parar e iniciar próxima** (interrompe e inicia a próxima tarefa enviada a ele). O mesmo controle aparece ao lado da tarefa no chat. Uma tarefa pausada pode ser retomada pela tela Tarefas. O Maestro espera o processo anterior encerrar antes de iniciar o seguinte; se a próxima tarefa precisar de aprovação para editar arquivos, ela aguardará essa aprovação. A tela de arquivos separa alterações preparadas e pendentes, mantém o explorador visível, abre abas e mostra diffs numerados em modo unificado ou lado a lado. Permite preparar e retirar arquivos, reverter mudanças e fazer commits.
- **Terminais:** abre várias sessões independentes do PowerShell em abas, sempre iniciando na pasta do projeto ativo. As sessões continuam abertas ao trocar de tela ou projeto e são encerradas quando a aba ou o Maestro fecha. **Janela externa** abre o Windows Terminal nessa mesma pasta para programas que exigem uma interface interativa completa.
- **Base de conhecimento:** importa arquivos ou pastas, extrai texto de PDF e DOCX, indexa localmente, busca trechos e controla quais agentes podem consultá-los. PDFs digitalizados ainda precisam de OCR externo.
- **MCPs:** cadastra servidores locais ou HTTP por projeto, importa JSON, ativa/desativa, edita e verifica a configuração. Os servidores ativos são passados ao Codex ao iniciar tarefas. A tela não verifica a conexão efetiva com cada servidor.
- **Plugins:** lista, instala e remove plugins usando o Codex CLI e permite adicionar uma fonte de marketplace. A instalação vale para a configuração do Codex neste computador.
- **Modelos e conexão:** mostra o estado real de autenticação do Codex CLI, permite iniciar o login, escolher o modelo e o nível de raciocínio por agente. O seletor inclui as opções mostradas no VS Code: GPT-6.1 Sol, GPT-6 Astra, GPT-6 Sol, GPT-6 Luna, GPT-5.6 Sol, GPT-5.6 Terra e GPT-5.6 Luna. O modelo automático é escolhido entre os que o CLI lista para a conta, evitando herdar uma configuração global incompatível. O Maestro prefere o Codex incluído na extensão do VS Code quando ele está instalado; um caminho escolhido manualmente prevalece. Se o CLI recusar um modelo ou outra etapa, o chat mostra o motivo recebido do Codex.
- **Configurações:** define a regra de escrita do projeto (permitir, aprovar por tarefa ou bloquear), reduz animações, exporta um backup ZIP e permite apagar o histórico do projeto.

O visual segue as imagens de referência fornecidas: tema escuro, barra lateral por projeto, cartões e painéis compactos. A marca e o ícone usam os arquivos em `src/main/resources/brand`. O ícone do Windows é gerado pelo código em `scripts/GenerateMaestroAssets.java`; após alterar o desenho, execute `java scripts/GenerateMaestroAssets.java src/main/resources/brand` antes de empacotar. Os dados e indicadores vistos nos modelos das telas eram ilustrativos; o aplicativo mostra apenas dados reais do projeto e do Codex CLI.

No Windows 11, a barra de título nativa usa a paleta do Maestro: fundo `#202020`, texto claro e borda `#303030`. Os controles padrão da janela continuam disponíveis. Em versões do Windows que não aceitam esses atributos, o sistema desenha a barra conforme seu próprio tema.

## Codex e permissões

O aplicativo usa o **Codex CLI instalado no computador**. Na tela **Modelos**, o cartão **Conta do Codex** mostra o estado do login. O Maestro procura o CLI no `PATH` e nas instalações comuns do Windows. Se não o encontrar, clique em **Localizar Codex CLI** e selecione `codex.cmd` ou `codex.exe`; o caminho fica salvo para as próximas aberturas. Como alternativa, defina `AGENTES_CODEX_PATH` com o caminho do executável.

Na mesma tela, **Uso do seu plano** consulta os limites da conta pelo [Codex app-server](https://learn.chatgpt.com/docs/app-server). As barras mostram o percentual já usado e quando cada janela reinicia. O ícone de reset abre os créditos disponíveis; o uso de um crédito exige confirmação, e **Cancelar** não consome nada. Se o Codex não informar uma janela ou detalhes dos créditos, o Maestro mostra essa indisponibilidade sem inventar percentuais.

Cada agente mantém uma sessão do Codex por conversa, sem reaproveitar o histórico de outra conversa. Agentes sem permissão de edição usam o sandbox `read-only`. Para agentes que podem editar, a regra padrão **Aprovar** pede confirmação antes de cada tarefa com escrita; **Permitir** inicia a tarefa sem essa confirmação; **Bloquear** usa leitura. O sandbox e as permissões efetivas de ferramentas continuam sendo aplicados pelo Codex CLI. Aprovações individuais de comandos ou acesso à web durante uma tarefa exigem integração futura com o app-server do Codex.

## Dados e limites atuais

Os dados ficam em `~/.agentes-local` por padrão. Defina `AGENTES_LOCAL_HOME` para escolher outra pasta. O backup ZIP exporta os dados, anexos e índices desta pasta; ele não inclui credenciais ou sessões guardadas pelo próprio Codex CLI.

A busca da base de conhecimento é textual e local, sem embeddings. Cada fonte tem limite de 25 MB de entrada, e o índice armazena até 2 milhões de caracteres extraídos. A primeira versão não faz OCR de PDFs digitalizados.

## Aplicativo para celular

As notificações Android de respostas dos agentes já estão implementadas: exibem nome, prévia e retrato estático, e abrem a conversa ao toque. Elas exigem um APK instalado e as credenciais FCM v1 no Expo EAS. O passo a passo está no README do repositório separado `maestro-mobile`.

O código está na pasta irmã `maestro-mobile/`, em outro repositório. A versão mobile usa a paleta escura do Maestro e oferece seleção de projeto, conversas em grupo e individuais, menções, mensagens com anexos e imagens, respostas citadas, elenco de agentes, tarefas, aprovações e perguntas. Na rede local, o celular consulta o computador a cada quatro segundos; pelo Firebase, consulta a cada 30 segundos enquanto o app está aberto. O Codex continua sendo executado pelo aplicativo desktop, com a mesma conta e as mesmas regras de aprovação.

### Acesso de qualquer lugar com Firebase

O computador precisa estar ligado, com Maestro e internet ativos. O Firebase guarda pedidos e respostas temporários em uma caixa postal; o conteúdo é cifrado com AES-256-GCM no celular e decifrado somente no computador. Não é necessário abrir portas no roteador nem mudar o perfil do Wi-Fi. O login do Firebase identifica o único usuário autorizado. A chave remota do Maestro é uma segunda chave, separada da senha do Firebase; guarde ambas. Os metadados de acesso, o número de partes e a frequência das mensagens ainda ficam visíveis ao Firebase. As mensagens e anexos passam também pelo Codex quando você manda o agente trabalhar.

1. Copie [`firebase/firestore.rules.example`](../firebase/firestore.rules.example) para `firebase/firestore.rules`, substitua `COLOQUE_SEU_UID_AQUI` pelo UID do usuário em Firebase Authentication e publique o conteúdo em **Firestore Database → Rules**. **Não use modo de teste nem regras públicas.**
2. Abra o Maestro pelo atalho da Área de Trabalho. Em **Configurações → Celular e acesso remoto → Acesso de qualquer lugar**, confira o projeto Firebase. Se não aparecer, selecione `maestro-mobile/src/firebaseConfig.local.ts`. Clique em **Entrar no Firebase** e informe o e-mail e a senha do usuário criado em **Authentication**. A senha não é salva pelo Maestro. Clique em **Verificar conexão**; o cartão deve mostrar que está conectado.
3. Clique em **Copiar chave remota** ou **Mostrar chave remota**. Essa chave tem **44 caracteres e termina em `=`**; a chave local de 16 caracteres não serve neste modo. No app do celular, escolha **De qualquer lugar**, entre com o mesmo e-mail e senha do Firebase e informe a chave remota. O app guarda a sessão e a chave no armazenamento seguro do aparelho. Não envie a senha nem a chave por mensagens.
4. Faça o primeiro teste com computador e celular ligados. Envie uma mensagem curta. Depois, com o celular na rede móvel, envie outra para confirmar o acesso fora do Wi-Fi.

O arquivo `maestro-mobile/src/firebaseConfig.local.ts` contém a configuração pública do cliente Firebase e fica fora do Git. Ele **não** contém a senha da conta. O token de sessão e a chave remota do computador ficam em `~/.agentes-local/firebase-remote.json`, dentro do perfil local do Windows. A caixa postal usa a franquia do plano Spark; o volume de consultas e anexos deve ser acompanhado no console Firebase.

O **Expo Go** precisa carregar o bundle do Metro no computador. Para abrir o app de qualquer lugar sem depender da rede local para esse carregamento, é necessário gerar e instalar um **APK**. O perfil `preview` em `maestro-mobile/eas.json` já pede um APK; a compilação pela EAS exige uma conta Expo e acesso ao serviço. O arquivo `.easignore` permite incluir a configuração pública Firebase no pacote enviado ao serviço de build; o e-mail, a senha, a sessão e a chave remota não fazem parte do código do APK.

### Teste pela rede local

**Se o Firewall bloquear o Metro pela rede local, use `npm run dev:tunnel` para testar no Expo Go.** O comando deve mostrar `Tunnel ready` e um endereço `exps://...exp.direct`. Encerre qualquer servidor Expo anterior com **Ctrl+C** antes de iniciá-lo. O túnel é temporário e público; mantenha o QR somente com você.

Para testar no celular:

1. Abra o **Maestro** pelo atalho da Área de Trabalho. Em **Configurações → Celular e acesso remoto**, veja o endereço e a chave de pareamento. Se o Windows perguntar sobre acesso à rede, permita na **rede privada**.
2. Conecte computador e celular à mesma rede Wi-Fi. Instale no celular uma versão do **Expo Go compatível com SDK 57**. Consulte [expo.dev/go](https://expo.dev/go) se o Expo Go informar incompatibilidade.
3. No computador, execute `start.cmd` ou `npm run dev` dentro da pasta irmã `maestro-mobile`. O terminal mostrará um QR code do Expo; abra-o no Expo Go. Mantenha esse terminal aberto durante o teste.
4. No app mobile, digite o endereço `http://IP-DO-COMPUTADOR:8787` e a chave mostrada no desktop. A chave é guardada no armazenamento seguro do celular. Em **Nova chave**, no desktop, você invalida as conexões anteriores.

O acesso usa HTTP com chave e aceita apenas conexões vindas da rede local. A chave não entra no backup ZIP. Como a conexão local não usa TLS, use uma rede Wi-Fi em que confie; não encaminhe a porta 8787 para a internet. O app mobile não trabalha quando o computador está desligado. O perfil `maestro-mobile/eas.json` está configurado para gerar um APK de distribuição interna com `eas build --platform android --profile preview`.

Se o Expo Go mostrar `Failed to download remote update`, pare o terminal com **Ctrl+C**, rode `npm run dev` novamente e leia o **novo** QR code. Se o endereço `http://IP-DO-COMPUTADOR:8081/status` não abrir no navegador do celular, ele não alcança o Metro pela rede local. Como alternativa de teste sem mudar o firewall, execute `npm run dev:tunnel` na pasta mobile; o QR de túnel usa um endereço público temporário. Não compartilhe esse QR e mantenha o terminal aberto. O túnel serve somente o app de desenvolvimento; o acesso aos dados do Maestro continua exigindo o pareamento Firebase.

## Estrutura

- `src/main/java/br/com/agenteslocal/AgentManagerApp.java`: interface JavaFX.
- `src/main/java/br/com/agenteslocal/model/WorkspaceData.java`: projetos, agentes, mensagens e tarefas.
- `src/main/java/br/com/agenteslocal/data/WorkspaceRepository.java`: armazenamento e backup local.
- `src/main/java/br/com/agenteslocal/codex/`: execução, autenticação e plugins do Codex CLI.
- `src/main/java/br/com/agenteslocal/knowledge/KnowledgeService.java`: extração, índice e busca de trechos.
- `src/main/java/br/com/agenteslocal/git/GitService.java`: visualização e ações do Git.
- `src/main/java/br/com/agenteslocal/terminal/TerminalSession.java`: sessões persistentes do PowerShell por projeto.
- `src/main/java/br/com/agenteslocal/mobile/`: API local autenticada para o aplicativo mobile.
- `maestro-mobile/` (repositório separado): aplicativo React Native/Expo e script de inicialização no computador.
- `src/main/resources/app.css`: tema visual.
- `src/main/resources/icons/`: ícones Lucide usados na interface desktop e licença ISC/MIT.
- `package.cmd` e `package.ps1`: empacotamento para Windows.
