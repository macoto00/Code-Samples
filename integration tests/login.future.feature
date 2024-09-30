Feature: Login Future

  Scenario Template: LoginFutureUserTokenResolver, LoginFutureUserTokenConsumer with LoginByCardResolver: Receive data and trigger login of relevant FutureUserTokens
    Given input Payload: for LoginFutureUserTokenSpecification with <knrNr> and <tactId>
    And DB create: future_user_tokens
      | futureUserTokenId                    | cardIdentifier | productionLineId                     | masterAreaId                         | tactId          | stationId                            | workplaceId                          | workplaceSlotId                      | loginType | active | knrNr   | vin               |
      | 8288a0fd-0489-4352-8924-f6923bf9b43f | CARD_SPRING    | aa485297-6579-49ce-b4d8-d35c59946775 | 288395d1-2080-4985-a888-82ef5768af67 | <tactId>        | 8ac23365-09ff-4a26-a96f-e52a67f2293e | 33f7b7f1-cc1d-4cbb-a46a-4a0197983eb6 | fb958ef5-f109-430c-b407-4d96c3dd1a50 | USER      | true   | <knrNr> | TMBJN7NP6R7037093 |
      | bdb73fa3-10e5-4d5a-9cea-7418bb47b1c3 | CARD_SUMMER    | aa485297-6579-49ce-b4d8-d35c59946775 | 288395d1-2080-4985-a888-82ef5768af67 | <tactId>        | 8ac23365-09ff-4a26-a96f-e52a67f2293e | 33f7b7f1-cc1d-4cbb-a46a-4a0197983eb6 | dab36f58-0be7-457b-a849-131c9fce0e56 | USER      | true   | <knrNr> | TMBJN7NP6R7037093 |
      | 8300b77b-a1aa-43c0-acb9-cd15a5b83aba | CARD_WINTER    | aa485297-6579-49ce-b4d8-d35c59946775 | 288395d1-2080-4985-a888-82ef5768af67 | <anotherTactId> | 8ac23365-09ff-4a26-a96f-e52a67f2293e | b4a834bc-0857-42e0-a577-6454febea192 | dfb1841a-d324-4000-948e-5dfbfb6b632a | USER      | true   | <knrNr> | TMBJN7NP6R7037093 |
    And integration MfaCheck: set up users with valid status for cards
      | cardIdentifier | personalIdentifier |
      | CARD_SPRING    | DZC_THOR           |
      | CARD_SUMMER    | DZC_ODIN           |
      | CARD_WINTER    | DZC_LOKI           |
    And integration ProductionLine: set up ProductionLine with aa485297-6579-49ce-b4d8-d35c59946775
    When produce Message: via RABBITMQ publisher to the "q.user-management.consumer.login-future-user-token"
    Then operation: wait for 2000 milliseconds
    And DB validate: no active FutureUserTokens exists for <tactId> and <knrNr>
    And DB validate: 2 users are logged in on the same workplace 33f7b7f1-cc1d-4cbb-a46a-4a0197983eb6
    And DB validate: 1 users are logged in on the same workplace slot fb958ef5-f109-430c-b407-4d96c3dd1a50
    And DB validate: 1 users are logged in on the same workplace slot dab36f58-0be7-457b-a849-131c9fce0e56
    And DB validate: 1 active FutureUserTokens exists for <anotherTactId> and <knrNr>
    And DB validate: 0 users are logged in on the same workplace b4a834bc-0857-42e0-a577-6454febea192
    And DB validate: 0 users are logged in on the same workplace slot dfb1841a-d324-4000-948e-5dfbfb6b632a
    Examples:
      | knrNr         | tactId                               | anotherTactId                        |
      | 3320234934577 | cecd8852-ef07-4d1f-a0df-c38e98a411fc | 3f2b4b1e-f6fd-4820-ad60-063253c80601 |

  Scenario Template: LoginFutureUserTokenResolver, LoginFutureUserTokenConsumer with LoginByCardResolver: Receive data and trigger login of Trainee when User is logged in
    Given DB create: users with cards
      | userId                               | personalIdentifier          | cardStatus | cardIdentifier          |
      | <userId>                             | <userPersonalIdentifier>    | ACTIVE     | <userCardIdentifier>    |
      | <traineeId>                          | <traineePersonalIdentifier> | ACTIVE     | <traineeCardIdentifier> |
      | f54c671a-a5cd-4213-98ae-5d72a8b7c02d | DZC_LOKI                    | ACTIVE     | CARD_THREE              |
    And integration MfaCheck: set up users with valid status for cards
      | cardIdentifier          | personalIdentifier          |
      | <userCardIdentifier>    | <userPersonalIdentifier>    |
      | <traineeCardIdentifier> | <traineePersonalIdentifier> |
      | CARD_THREE              | DZC_LOKI                    |
    And integration ProductionLine: set up ProductionLine with <prodLineId>
    # USER login
    Given input Payload: LoginSpecification for USER with card <userCardIdentifier> on <prodLineId> and <masterAreaId> and <tactId> and <workplaceId> and <workplaceSlotId>
    When request: POST "/login" is made
    Then response: should indicate OK
    # Trigger future login of TRAINEE
    Given input Payload: for LoginFutureUserTokenSpecification with <knrNr> and <tactId>
    And DB create: future_user_tokens
      | futureUserTokenId                    | cardIdentifier          | productionLineId | masterAreaId   | tactId          | stationId                            | workplaceId                          | workplaceSlotId                      | userId                               | loginType | active | knrNr   | vin               |
      | 8288a0fd-0489-4352-8924-f6923bf9b43f | <traineeCardIdentifier> | <prodLineId>     | <masterAreaId> | <tactId>        | 8ac23365-09ff-4a26-a96f-e52a67f2293e | <workplaceId>                        | <workplaceSlotId>                    | <traineeId>                          | TRAINEE   | true   | <knrNr> | TMBJN7NP6R7037093 |
      | 8300b77b-a1aa-43c0-acb9-cd15a5b83aba | CARD_THREE              | <prodLineId>     | <masterAreaId> | <anotherTactId> | 8ac23365-09ff-4a26-a96f-e52a67f2293e | b4a834bc-0857-42e0-a577-6454febea192 | dfb1841a-d324-4000-948e-5dfbfb6b632a | f54c671a-a5cd-4213-98ae-5d72a8b7c02d | USER      | true   | <knrNr> | TMBJN7NP6R7037093 |
    When produce Message: via RABBITMQ publisher to the "q.user-management.consumer.login-future-user-token"
    Then operation: wait for 2000 milliseconds
    And DB validate: no active FutureUserTokens exists for <tactId> and <knrNr>
    And DB validate: 2 users are logged in on the same workplace <workplaceId>
    And DB validate: 2 users are logged in on the same workplace slot <workplaceSlotId>
    And DB validate: 0 users are logged in on the same workplace b4a834bc-0857-42e0-a577-6454febea192
    And DB validate: 0 users are logged in on the same workplace slot dfb1841a-d324-4000-948e-5dfbfb6b632a
    And DB validate: 1 active FutureUserTokens exists for <anotherTactId> and <knrNr>
    Examples:
      | knrNr         | prodLineId                           | masterAreaId                         | workplaceId                          | workplaceSlotId                      | tactId                               | anotherTactId                        | userId                               | traineeId                            | userPersonalIdentifier | traineePersonalIdentifier | userCardIdentifier | traineeCardIdentifier |
      | 3320234934588 | b371c702-4375-444f-8f38-e89be8c71319 | 5f79e0de-3996-4193-8174-45baed217e3d | a79fc7ab-6544-4839-8ce0-fcba64ffef4c | 3f504e5d-64e5-4f3f-950d-b586388df2a2 | b382d327-5c63-4ee0-8889-23ed2c9cc28e | a6b2e5fc-f902-4055-9a42-825f1ff92185 | 83e0cd7a-804c-4505-8d2d-acb7f2059f5b | 2e69d0b2-4e3b-4a30-816e-453a0f2ea074 | DZC_TOOLONG            | DZC_TOOSHORT              | CARD_ONE           | CARD_TWO              |
