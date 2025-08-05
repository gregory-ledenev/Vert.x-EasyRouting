document.addEventListener('DOMContentLoaded', () => {
    const userForm = document.getElementById('user-form');
    const userList = document.getElementById('user-list');
    const userNameInput = document.getElementById('user-name');
    const userEmailInput = document.getElementById('user-email');
    const userIdInput = document.getElementById('user-id');
    const submitButton = document.getElementById('submit-button');
    const cancelButton = document.getElementById('cancel-button'); // New element

    const API_URL = '/api/users';
    let isEditing = false;

    // A reusable function to reset the form and editing state
    function resetFormState() {
        isEditing = false;
        userForm.reset();
        submitButton.textContent = 'Add User';
        cancelButton.style.display = 'none';
        userNameInput.focus();
    }

    // Fetch and render users from the web service
    async function fetchUsers() {
        try {
            const response = await fetch(API_URL);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const users = await response.json();
            renderUsers(users);
        } catch (error) {
            console.error('Error fetching users:', error);
            alert('Could not fetch users. Please check the backend service.');
        }
    }

    // Render the user list on the page
    function renderUsers(users) {
        userList.innerHTML = '';
        if (users.length === 0) {
            userList.innerHTML = '<tr><td colspan="3" class="no-users">No users found.</td></tr>';
            return;
        }

        users.forEach(user => {
            const row = document.createElement('tr');
            const userId = user._id || user.id;
            row.innerHTML = `
                <td>${user.name}</td>
                <td>${user.email}</td>
                <td class="actions">
                    <button class="edit-button" data-id="${userId}">Edit</button>
                    <button class="delete-button" data-id="${userId}">Delete</button>
                </td>
            `;
            userList.appendChild(row);
        });
    }

    // Handle form submission (Add or Edit)
    userForm.addEventListener('submit', async (e) => {
        e.preventDefault();

        const name = userNameInput.value.trim();
        const email = userEmailInput.value.trim();

        if (!name || !email) {
            alert('Name and Email are required!');
            return;
        }

        const userData = { name, email };

        try {
            if (isEditing) {
                // Update existing user
                const userId = userIdInput.value;
                const response = await fetch(`${API_URL}/${userId}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(userData)
                });
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
            } else {
                // Add new user
                const response = await fetch(API_URL, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(userData)
                });
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
            }

            resetFormState(); // Reset the form after a successful submission
            fetchUsers(); // Refresh the user list
        } catch (error) {
            console.error('Error submitting form:', error);
            alert('Failed to save user. Please try again.');
        }
    });

    // Handle Edit and Delete actions
    userList.addEventListener('click', async (e) => {
        const target = e.target;
        const userId = target.getAttribute('data-id');

        if (!userId) return;

        if (target.classList.contains('delete-button')) {
            if (!confirm('Are you sure you want to delete this user?')) return;
            try {
                const response = await fetch(`${API_URL}/${userId}`, { method: 'DELETE' });
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                fetchUsers(); // Refresh the user list
            } catch (error) {
                console.error('Error deleting user:', error);
                alert('Failed to delete user.');
            }
        } else if (target.classList.contains('edit-button')) {
            // Find the user to edit and populate the form
            const row = target.closest('tr');
            const name = row.children[0].textContent;
            const email = row.children[1].textContent;

            userNameInput.value = name;
            userEmailInput.value = email;
            userIdInput.value = userId;
            submitButton.textContent = 'Save Changes';
            cancelButton.style.display = 'inline-block'; // Show the cancel button
            isEditing = true;
            userNameInput.focus();
        }
    });

    // Handle Cancel button click
    cancelButton.addEventListener('click', () => {
        resetFormState();
    });

    // Initial fetch of users when the page loads
    fetchUsers();
});