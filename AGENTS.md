# CodeDog maintenance rules

- Keep this project independent from the CodeMao application. The only shared resource is the `codemao_default` Docker network used by the public ACME challenge proxy.
- Never commit `.env`, MySQL data, legacy SQLite databases, migration archives, SSH keys, or backup checksums.
- Keep administrator credentials outside Git. Require `ADMIN_PASSWORD` from the untracked `.env`; deployment automation must never overwrite an existing administrator password.
- Run the complete test suite before deployment.
- After deployment, wait for the container to become healthy and verify both anonymous viewing and authenticated editing.
- After every healthy deployment, run `./ops/trigger-async-backup.sh post-deploy` and confirm `codedog-backup.service` succeeds.
- Migration archives contain the administrator credential configuration, documents, and student data. Keep them off public storage.
