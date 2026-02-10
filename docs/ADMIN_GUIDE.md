# CQL Platform Administrator Guide

## 1. Default Administrator Account

When the CQL Platform starts for the first time, a default administrator account is automatically created:

- **Username:** `admin`
- **Password:** `admin`

> **Important:** Change the default password immediately after the first login. Navigate to your profile settings and use the Change Password feature.

## 2. Accessing User Management

1. Log in with an administrator account
2. Click the **Users** link in the navigation sidebar
3. The User Management page displays all registered users

## 3. Creating a New User

1. Click the **Create User** button at the top-right of the User Management page
2. Fill in the required fields:
   - **Username** (required): 3-50 characters
   - **Password** (required): minimum 6 characters
   - **Email** (optional)
   - **Role**: Select `USER` or `ADMIN`
3. Click **Create**
4. The new user will appear in the user list

## 4. Changing User Roles

1. In the user list, locate the user whose role you want to change
2. Use the **Role** dropdown in that user's row to select the new role (`ADMIN` or `USER`)
3. A confirmation dialog will appear -- click **Confirm** to apply the change

**Restrictions:**
- Administrators cannot change their own role (the dropdown is disabled for your own account)
- This prevents accidentally removing the last administrator from the system

## 5. Enabling / Disabling User Accounts

1. In the user list, locate the target user
2. Toggle the **switch** next to the user's status chip
3. A confirmation dialog will appear -- click **Confirm**

| Status | Meaning |
|--------|---------|
| Active | User can log in and use the platform |
| Disabled | User cannot log in; existing sessions are not terminated |

**Restrictions:**
- Administrators cannot disable their own account (the switch is disabled for your own account)

## 6. Resetting a User's Password

1. Click the **Reset Password** button in the user's row
2. A dialog will display the temporary password
3. Copy the temporary password and share it securely with the user
4. The user will be required to change the password upon next login

## 7. Security Recommendations

1. **Change the default password** -- The `admin/admin` account should have its password changed immediately after first deployment
2. **Maintain at least one administrator** -- The system prevents self-demotion and self-disabling, but ensure you always have at least one active admin account
3. **Use strong passwords** -- Enforce a minimum of 6 characters; recommend mixing uppercase, lowercase, numbers, and special characters
4. **Review user accounts regularly** -- Disable accounts for users who no longer need access rather than deleting them
5. **Limit admin accounts** -- Only grant the ADMIN role to users who need administrative capabilities
6. **Share temporary passwords securely** -- When resetting passwords, use a secure channel to communicate the temporary password to the user
