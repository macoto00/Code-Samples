Feature: login

  // skipping other tests

  Scenario Template: LoginByCardResolver: Logging in trainee and logout another already logged in trainee
    Given input Payload: LoginSpecification for TRAINEE with card <newTraineeCardIdentifier> on <workplaceSlotId> and <prodLineId> and <masterAreaId>
    And integration MfaCheck: set up user with identifier <newTraineePersonalIdentifier> with VALID card <newTraineeCardIdentifier>
    And integration ProductionLine: set up ProductionLine with <prodLineId>
    And integration ProductionLine: set up WorkplaceSlot with <prodLineId> and MultipleLoginHandling as <multipleLoginHandling>
    And DB create: users with cards
      | personalIdentifier             | cardStatus | cardIdentifier             |
      | <newTraineePersonalIdentifier> | ACTIVE     | <newTraineeCardIdentifier> |
    And DB create: users with cards and user_tokens
      | personalIdentifier       | workplaceSlotId   | loginType | prodLineId   | masterAreaId   |
      | <userPersonalIdentifier> | <workplaceSlotId> | USER      | <prodLineId> | <masterAreaId> |
      | DZC1115                  | <workplaceSlotId> | TRAINEE   | <prodLineId> | <masterAreaId> |
    When request: POST "/login" is made
    Then response: should indicate OK
    And DB validate: 2 users are logged in on the same workplace slot <workplaceSlotId>
    And DB validate: USER with identifier <userPersonalIdentifier> is logged in on the workplace slot <workplaceSlotId>
    And DB validate: TRAINEE with identifier <newTraineePersonalIdentifier> is logged in on the workplace slot <workplaceSlotId>
    Examples:
      | workplaceSlotId                      | newTraineeCardIdentifier | newTraineePersonalIdentifier | userPersonalIdentifier | prodLineId                           | masterAreaId                         | multipleLoginHandling   |
      | ee933fac-88ba-4651-b3e3-d1ff33e1a527 | 123412345                | DZC5555                      | DZC1114                | 3f4b1aa8-5cc9-4b79-9725-7bd4e47e5600 | 3f4b1aa8-5cc9-4b79-9725-7bd4e47e5601 | INHERIT_PRODUCTION_LINE |
      | ee933fac-88ba-4651-b3e3-d1ff33e1a527 | 123412345                | DZC5555                      | DZC1114                | 3f4b1aa8-5cc9-4b79-9725-7bd4e47e5600 | 3f4b1aa8-5cc9-4b79-9725-7bd4e47e5601 | FORBID_MULTIPLE_LOGIN   |
      | ee933fac-88ba-4651-b3e3-d1ff33e1a527 | 123412345                | DZC5555                      | DZC1114                | 3f4b1aa8-5cc9-4b79-9725-7bd4e47e5600 | 3f4b1aa8-5cc9-4b79-9725-7bd4e47e5601 | ALLOW_MULTIPLE_LOGIN    |

  Scenario Template: LoginByCardResolver: Logged in User logging on another Workplace Slot when Production Line supports multiple login and Workplace Slot INHERIT_PRODUCTION_LINE, ALLOW_MULTIPLE_LOGIN
    Given input Payload: LoginSpecification for USER with card <userCardIdentifier> on <workplaceSlotIdTwo> and <prodLineId> and <masterAreaId>
    And integration MfaCheck: set up user with identifier <userPersonalIdentifier> with VALID card <userCardIdentifier>
    And integration ProductionLine: set up ProductionLine with <prodLineId> that supports multiple logins
    And integration ProductionLine: set up WorkplaceSlot with <prodLineId> and MultipleLoginHandling as <multipleLoginHandling>
    And DB create: users with cards and user_tokens
      | personalIdentifier       | workplaceSlotId      | loginType | prodLineId   | masterAreaId   |
      | <userPersonalIdentifier> | <workplaceSlotIdOne> | USER      | <prodLineId> | <masterAreaId> |
    When request: POST "/login" is made
    Then response: should indicate OK
    And DB validate: USER with identifier <userPersonalIdentifier> is logged in on the workplace slot <workplaceSlotIdOne>
    And DB validate: USER with identifier <userPersonalIdentifier> is logged in on the workplace slot <workplaceSlotIdTwo>
    And DB validate: User with <userPersonalIdentifier> is logged in on different workplace slots simultaneously like <workplaceSlotIdOne> and <workplaceSlotIdTwo>
    Examples:
      | workplaceSlotIdOne                   | workplaceSlotIdTwo                   | userCardIdentifier | userPersonalIdentifier | prodLineId                           | masterAreaId                         | multipleLoginHandling   |
      | ee933fac-88ba-4651-b3e3-d1ff33e1a521 | ee933fac-88ba-4651-b3e3-d1ff33e1a527 | 123412345          | DZC5555                | 3f4b1aa8-5cc9-4b79-9725-7bd4e47e5601 | 3f4b1aa8-5cc9-4b79-9725-7bd4e47e5601 | INHERIT_PRODUCTION_LINE |
      | ee933fac-88ba-4651-b3e3-d1ff33e1a521 | ee933fac-88ba-4651-b3e3-d1ff33e1a527 | 123412345          | DZC5555                | 3f4b1aa8-5cc9-4b79-9725-7bd4e47e5601 | 3f4b1aa8-5cc9-4b79-9725-7bd4e47e5601 | ALLOW_MULTIPLE_LOGIN    |

  Scenario Template: LoginByCardResolver: Logged in User logging on another Workplace Slot when Production Line supports multiple login and Workplace Slot FORBID_MULTIPLE_LOGIN
    Given input Payload: LoginSpecification for USER with card <userCardIdentifier> on <workplaceSlotIdTwo> and <prodLineId> and <masterAreaId>
    And integration MfaCheck: set up user with identifier <userPersonalIdentifier> with VALID card <userCardIdentifier>
    And integration ProductionLine: set up ProductionLine with <prodLineId> that supports multiple logins
    And integration ProductionLine: set up WorkplaceSlot with <prodLineId> and MultipleLoginHandling as <multipleLoginHandling>
    And DB create: users with cards and user_tokens
      | personalIdentifier       | workplaceSlotId      | loginType | prodLineId   | masterAreaId   |
      | <userPersonalIdentifier> | <workplaceSlotIdOne> | USER      | <prodLineId> | <masterAreaId> |
    When request: POST "/login" is made
    Then response: should indicate OK
    And DB validate: User with identifier <userPersonalIdentifier> is not logged in on the workplace slot <workplaceSlotIdOne>
    And DB validate: USER with identifier <userPersonalIdentifier> is logged in on the workplace slot <workplaceSlotIdTwo>
    Examples:
      | workplaceSlotIdOne                   | workplaceSlotIdTwo                   | userCardIdentifier | userPersonalIdentifier | prodLineId                           | masterAreaId                         | multipleLoginHandling |
      | ee933fac-88ba-4651-b3e3-d1ff33e1a521 | ee933fac-88ba-4651-b3e3-d1ff33e1a527 | 123412345          | DZC5555                | 3f4b1aa8-5cc9-4b79-9725-7bd4e47e5601 | 3f4b1aa8-5cc9-4b79-9725-7bd4e47e5601 | FORBID_MULTIPLE_LOGIN |

