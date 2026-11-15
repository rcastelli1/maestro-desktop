# Preparar os repositórios para o GitHub

O desktop (`terminal-agentes/`) e o celular (`maestro-mobile/`) são projetos separados. O [repositório do desktop](https://github.com/rcastelli1/maestro-desktop) é público; o mobile não é enviado junto com ele. Revise a licença e os direitos dos arquivos de mídia antes de redistribuir o aplicativo.

## Antes de publicar

1. Execute `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify.ps1` neste projeto.
2. Revise `git status --short` e `git ls-files` nos dois projetos. Não adicione `target/`, `.tools/`, `node_modules/`, APKs, dados de `~/.agentes-local` ou arquivos de credenciais.
3. O arquivo `firebase/firestore.rules` contém as regras da sua conta e fica ignorado. O arquivo versionável é `firebase/firestore.rules.example`; cada instalação deve substituir `COLOQUE_SEU_UID_AQUI` por seu UID numa cópia local antes de publicar as regras.
4. No mobile, `src/firebaseConfig.local.ts` e `google-services.json` continuam locais e ignorados. `src/firebaseConfig.example.ts` mostra o formato necessário. A chave privada de conta de serviço do Firebase deve ficar fora dos dois projetos.
5. A chave de API do app Web Firebase identifica o projeto, mas [valores incluídos no app cliente são legíveis para quem instala o aplicativo](https://docs.expo.dev/eas/environment-variables/). Não coloque senhas, chave remota do Maestro ou JSON de conta de serviço no código.
6. Use um e-mail `noreply` do GitHub nos metadados dos commits. O `.gitignore` exclui backups `.zip`/`.maestrobackup` e arquivos locais de sessão, mas confirme o índice antes de cada push.

O [GitHub recomenda ignorar arquivos sensíveis e verificar segredos antes do envio](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository). A proteção de push ajuda, mas não substitui a revisão local.

## Desktop

O remoto do desktop já está configurado para `https://github.com/rcastelli1/maestro-desktop.git`. Para enviar novas alterações:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-public-files.ps1
git status --short
git push -u origin main
```

Não execute `git add -f` em arquivos ignorados e envie apenas `main`, sem usar `--all` ou `--mirror`.

## Mobile

A pasta `maestro-mobile/` também tem um commit local na branch `main`. Crie outro repositório privado **vazio** e execute nela:

```powershell
npm ci
npx tsc --noEmit
git status --short
git remote add origin https://github.com/SEU-USUARIO/maestro-mobile.git
git push -u origin main
```

O repositório desktop não cria um APK. Builds Android continuam pelo EAS conforme o `README.md` do projeto mobile. Se algum segredo já tiver sido enviado ao GitHub, remova-o do histórico e substitua a credencial; apagar só o arquivo na versão mais recente não elimina o vazamento.
