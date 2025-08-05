document.addEventListener('DOMContentLoaded', () => {
    // Check if the user is signed in. If not, redirect to login.
    const token = localStorage.getItem('token');
    if (!token) {
        window.location.href = 'login.html';
        return;
    }

    const userForm = document.getElementById('user-form');
    const userList = document.getElementById('user-list');
    const userNameInput = document.getElementById('user-name');
    const userEmailInput = document.getElementById('user-email');
    const userIdInput = document.getElementById('user-id');
    const submitButton = document.getElementById('submit-button');
    const cancelButton = document.getElementById('cancel-button');
    const signoutButton = document.getElementById('signout-button');

    const API_URL = 'http://localhost:8080/api/users';
    let isEditing = false;

    // Helper function to make API calls with the JWT
    async function authFetch(url, options = {}) {
        const token = localStorage.getItem('token');
        const headers = {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
            ...options.headers
        };

        const response = await fetch(url, { ...options, headers });

        // If the token is invalid, redirect to login
        if (response.status === 401 || response.status === 403) {
            localStorage.removeItem('token');
            window.location.href = 'login.html';
            throw new Error('Authentication failed.');
        }

        return response;
    }

    // Renders the list of users to the table
    function renderUsers(users) {
        userList.innerHTML = '';
        if (users.length === 0) {
            userList.innerHTML = '<tr><td colspan="3" class="no-users">No users found.</td></tr>';
            return;
        }

        users.forEach(user => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${user.name}</td>
                <td>${user.email}</td>
                <td class="actions">
                    <button class="edit-button" data-id="${user.id}">Edit</button>
                    <button class="delete-button" data-id="${user.id}">Delete</button>
                </td>
            `;
            userList.appendChild(row);
        });
    }

    // A reusable function to reset the form and editing state
    function resetFormState() {
        isEditing = false;
        userForm.reset();
        submitButton.textContent = 'Add User';
        cancelButton.style.display = 'none';
        userNameInput.focus();
    }

    // Fetches users from the API
    async function fetchUsers() {
        try {
            const response = await authFetch(API_URL);
            if (!response.ok) throw new Error('Failed to fetch users');
            const users = await response.json();
            renderUsers(users);
        } catch (error) {
            console.error('Error:', error);
        }
    }

    // Handles form submission (Add or Edit)
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
                const userId = userIdInput.value;
                await authFetch(`${API_URL}/${userId}`, { method: 'PUT', body: JSON.stringify(userData) });
            } else {
                await authFetch(API_URL, { method: 'POST', body: JSON.stringify(userData) });
            }

            resetFormState();
            fetchUsers();
        } catch (error) {
            console.error('Error submitting form:', error);
        }
    });

    // Handles Edit and Delete actions
    userList.addEventListener('click', async (e) => {
        const target = e.target;
        const userId = target.getAttribute('data-id');

        if (!userId) return;

        if (target.classList.contains('delete-button')) {
            if (!confirm('Are you sure you want to delete this user?')) return;
            try {
                await authFetch(`${API_URL}/${userId}`, { method: 'DELETE' });
                fetchUsers();
            } catch (error) {
                console.error('Error deleting user:', error);
            }
        } else if (target.classList.contains('edit-button')) {
            const row = target.closest('tr');
            const name = row.children[0].textContent;
            const email = row.children[1].textContent;

            userNameInput.value = name;
            userEmailInput.value = email;
            userIdInput.value = userId;
            submitButton.textContent = 'Save Changes';
            cancelButton.style.display = 'inline-block';
            isEditing = true;
            userNameInput.focus();
        }
    });

    // Handle Cancel button click
    cancelButton.addEventListener('click', () => {
        resetFormState();
    });

    // Handle Sign Out button click
    signoutButton.addEventListener('click', () => {
        localStorage.removeItem('token');
        window.location.href = 'login.html';
    });

    // Initial fetch of users when the page loads
    fetchUsers();
});