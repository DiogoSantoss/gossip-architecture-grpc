# Relatório
## Turmas — 3º Período 2021/2022

Realizado por:
- Diogo Santos - 95562
- João Ivo - 90115
- Miguel Porfírio - 95641


Para resolver o problema da concorrência proposto na 3.ª fase do projeto, decidimos usar relógios vetoriais com uma arquitetura inspirada na Lazy Replication.

Cada servidor guarda um relógio vetorial (*Svec*) que incrementa após cada pedido bem sucedido do cliente ou após um gossip.
Cada cliente (exceto o admin) guarda também um relógio vetorial (*Cvec*) que recebe do servidor após um pedido.

O cliente ao enviar um pedido ao servidor, envia também o seu relógio vetorial. O servidor contactado confirma
que o seu relógio está coerente com o do cliente, caso contrário (se *Svec "happens-before" Cvec*), irá atualizar-se antes de responder ao pedido.
Esta verificação garante consistência de leitura no lado do cliente.

A atualização dos servidores é feita usando o protocolo gossip, executado em 3 situações:
1. Através de um *timer* que corre a cada segundo.
2. Após um pedido do cliente, na situação em que um servidor precisa de se atualizar antes de responder.
3. A partir do comando *gossip* do admin que força o gossip.

Um servidor ao iniciar o gossip, envia para todos os outros servidores um pedido que contém:
- O estado da turma 
- O relógio vetorial
- O qualificador
- O id do servidor

Cada servidor, ao receber este pedido, tem de verificar se está "à frente", "atrás" ou "concorrente" com o servidor que enviou o pedido.
- No caso de estar "à frente", não altera o seu estado e responde com o estado da sua turma e o seu relógio.
- No caso de estar "atrás", substitui o seu estado pelo do pedido e responde com o estado da sua turma e o seu relógio (atualizados).
- No caso de estar "concorrente", procede à resolução de conflitos entre os dois servidores e responde com o resultado.

A resolução de conflitos é feita de duas formas diferentes:
- Para os atributos *openEnrollments* e *capacity* priorizamos o servidor primário ou o servidor que estiver mais "up-to-date" com ele.
- Para os atributos *enrolled* e *discarded* utilizamos *timestamps* associados a cada *student*, em que priorizamos estudantes inscritos à mais tempo, e no caso de um estar *enrolled* e *discarded* em servidores diferentes priorizamos o estado mais recente.

O servidor ao receber a resposta dos vários pedidos de gossip atualiza-se com o estado de um deles. 
No caso de ter sido um gossip iniciado por necessidade de ser atualizar perante um pedido do cliente, garantimos que o estado escolhido para 
se atualizar é consistente com o relógio do cliente.

Esta solução permite a existência de 1 servidor primário e 2 secundários e garante a coerência eventual do sistema.

Para facilitar o teste (e debug) do sistema, os vários comandos do admin aceitam como argumento:
- P para desativar o primário 
- S para desativar um secundário (aleatório)
- SX para desativar um secundário específico em que X é o número do servidor secundário atribuido pelo naming server (e.g. S1,S2,etc)