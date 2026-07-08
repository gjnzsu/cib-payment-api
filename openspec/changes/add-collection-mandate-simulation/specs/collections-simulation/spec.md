## MODIFIED Requirements

### Requirement: Keep mandate lifecycle out of collection runtime scope
The system SHALL NOT create, amend, activate, expire, or verify mandates or eDDA authorizations inside collection creation. The system SHALL allow collection requests to use external mandate references as simulator evidence, and SHALL allow collection requests to use active mandate references created by the mandate simulation API.

#### Scenario: External authorization reference is provided
- **GIVEN** a client submits a supported collection request
- **WHEN** the request includes a mandate reference or direct debit authorization reference that does not match a system-created mandate owned by the authenticated client
- **THEN** the system SHALL treat the reference as simulator input evidence of an existing external authorization
- **AND** the system SHALL NOT create a mandate resource

#### Scenario: Active simulated mandate reference is provided
- **GIVEN** a client owns a mandate created by the mandate simulation API
- **AND** the mandate status is `ACTIVE`
- **WHEN** the client submits a supported collection request using that mandate reference
- **THEN** the system SHALL accept the mandate reference for collection simulation
- **AND** the system SHALL NOT create a new mandate resource

#### Scenario: Inactive simulated mandate reference is rejected
- **GIVEN** a client owns a mandate created by the mandate simulation API
- **AND** the mandate status is not `ACTIVE`
- **WHEN** the client submits a supported collection request using that mandate reference
- **THEN** the system SHALL reject the request with a consistent validation error
- **AND** the system SHALL NOT create a collection record

#### Scenario: Mandate lifecycle request is outside collection scope
- **GIVEN** a client attempts to create, amend, cancel, activate, or expire a mandate through the collections API
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request as unsupported by the current collection simulator profile
