# Arquitetura do código

## Decisão

O desktop é um **monólito modular**: um processo e um executável, com módulos lógicos para chat, execução dos agentes, conhecimento, persistência e acesso mobile. O celular continua em um projeto React Native/Expo separado, pois tem ciclo de build próprio e já foi colocado em outra pasta por escolha do usuário. Não há necessidade atual de introduzir Spring Boot, microserviços ou um banco remoto como fonte principal de dados.

Essa escolha combina separação de responsabilidades dentro de um único aplicativo com a simplicidade de instalação local. A orientação de [camadas e dependências voltadas ao núcleo](https://learn.microsoft.com/en-us/dotnet/standard/modern-web-apps-azure-architecture/common-web-application-architectures) e de [interfaces explícitas entre módulos](https://docs.spring.io/spring-modulith/reference/fundamentals.html) fundamenta a direção; Spring Modulith em si **não** foi adicionado ao projeto JavaFX.

## Estrutura atual

```text
src/main/java/br/com/agenteslocal/
  AgentManagerApp.java       Inicialização, telas JavaFX e coordenação ainda concentrada
  application/              Casos de uso compartilhados; começou pelo agendamento
  model/                    Estado persistido: projetos, agentes, chats e tarefas
  chat/                     Menções e regras da fila
  codex/                    Adaptador do Codex CLI, modelos, plugins e contexto
  data/                     Persistência JSON e anexos
  knowledge/                Extração, índice e busca textual local
  mobile/                   API HTTP, ponte Firebase e notificações
  git/ terminal/            Integrações com o sistema operacional
  window/ character/        Interface e animações
```

`WorkspaceData` é a fonte de verdade em memória; `WorkspaceRepository` salva o estado em `~/.agentes-local/workspace.json` e armazena anexos em arquivos. O Firestore funciona como caixa postal de transporte cifrado, não como banco principal das conversas.

O `MobileApi` recebe uma solicitação da API local ou da ponte Firebase e chama as mesmas ações do desktop. O `ScheduledMessageService` é o primeiro caso de uso extraído da interface: valida, persiste, desfaz alterações em caso de falha e remove anexos sem uso ao cancelar.

## Direção das dependências

```text
JavaFX e MobileApi  ->  casos de uso  ->  modelo/regras
                          |                  ^
                          v                  |
                   persistência e adaptadores externos
```

As regras que escolhem destinatário, controlam fila, aprovam escrita e agendam mensagens devem morar em casos de uso reutilizáveis pelo desktop e pela API. Classes de JavaFX devem montar telas e traduzir ações do usuário. A execução do Codex, a persistência, Git, Firebase e sistema de arquivos devem ficar atrás de adaptadores. Ainda há referências diretas entre essas partes; o diagrama descreve a **meta de migração**, não uma garantia já imposta pelo compilador.

## Fluxo de uma mensagem

1. JavaFX ou `MobileApi` recebe a mensagem e resolve conversa e agente.
2. A aplicação cria a mensagem e a tarefa no projeto; se o agente está ocupado, a tarefa entra na fila.
3. `CodexService` inicia `codex exec --json` na pasta do projeto. A sessão do Codex é mantida por agente e conversa.
4. O retorno atualiza o modelo, é salvo em JSON e aparece nas duas interfaces. Notificações Android são enviadas pelo serviço de push da Expo.

## Threads e limites

A cena JavaFX e as mutações do estado compartilhado são tratadas na thread da interface; Codex, plugins e consulta ao Firebase usam trabalhadores em segundo plano. A [documentação de concorrência JavaFX](https://docs.oracle.com/javase/8/javafx/interoperability-tutorial/concurrency.htm) recomenda manter operações demoradas fora da thread da interface. Novos casos de uso devem preservar essa regra e definir claramente quem pode alterar `WorkspaceData`.

O estado atual ainda tem dois pontos de concentração: `AgentManagerApp.java` reúne telas e coordenação; `maestro-mobile/App.tsx` reúne a maioria das telas e estados React. A migração segura é incremental:

1. Extrair envio de mensagem, fila e execução de tarefas para casos de uso Java sem dependência de JavaFX.
2. Separar modelos de domínio dos objetos de persistência e dos DTOs da API mobile.
3. Introduzir interfaces pequenas para armazenamento, execução do Codex e entrega de eventos quando houver mais de uma implementação ou necessidade de teste isolado.
4. Dividir `App.tsx` em telas, hooks e serviços de dados, preservando o contrato atual da API.
5. Adicionar testes de contrato entre os dois repositórios antes de alterar campos ou rotas compartilhados.

Um monorepo Expo também é suportado, mas [acrescenta configuração de workspaces](https://docs.expo.dev/guides/monorepos/). Como os projetos já estão em pastas separadas por preferência do usuário, dois repositórios com versões e documentação coordenadas são a escolha atual.
