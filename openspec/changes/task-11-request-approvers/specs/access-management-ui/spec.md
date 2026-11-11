## ADDED Requirements

### Requirement: Заявитель видит согласующих
Таблица My Requests SHALL содержать колонку Approvers с именами согласующих из поля `approvers`; имя с известным e-mail MUST быть ссылкой `mailto:`. Карточка запроса, открытого заявителем по ссылке из письма, в состоянии ожидания SHALL показывать тот же список вместо безличного «Waiting for the owner's decision». При пустом списке показывается «-».

#### Scenario: Заявитель смотрит, кого пинать
- **WHEN** `alice` открывает My Requests с ожидающим запросом на `team-a.orders`
- **THEN** в колонке Approvers — `owner-a` со ссылкой `mailto:owner-a@example.com`

#### Scenario: Карточка заявителя
- **WHEN** `alice` открывает `/ui/<cluster>/access-management?request=<id>` своего ожидающего запроса
- **THEN** карточка показывает «Waiting for the decision of: owner-a»
