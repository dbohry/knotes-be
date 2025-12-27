const API_BASE = '/api/notes';
let currentNoteId = null;
let autoSaveTimeout = null;
let lastSavedContent = '';

// Theme management
function initializeTheme() {
    // Get saved theme preference or default to light
    const savedTheme = localStorage.getItem('theme') || 'light';
    const body = document.body;
    const themeSwitch = document.getElementById('themeSwitch');

    if (!themeSwitch) {
        console.error('Theme switch element not found');
        return;
    }

    // Apply the theme
    if (savedTheme === 'dark') {
        body.setAttribute('data-theme', 'dark');
        themeSwitch.classList.add('dark');
    } else {
        body.removeAttribute('data-theme');
        themeSwitch.classList.remove('dark');
    }
}

function toggleTheme() {
    const body = document.body;
    const themeSwitch = document.getElementById('themeSwitch');

    if (!themeSwitch) {
        console.error('Theme switch element not found');
        return;
    }

    // Toggle between light and dark themes
    const currentTheme = body.getAttribute('data-theme');

    if (currentTheme === 'dark') {
        // Switch to light theme
        body.removeAttribute('data-theme');
        themeSwitch.classList.remove('dark');
        localStorage.setItem('theme', 'light');
    } else {
        // Switch to dark theme
        body.setAttribute('data-theme', 'dark');
        themeSwitch.classList.add('dark');
        localStorage.setItem('theme', 'dark');
    }

    // Update theme color for mobile browsers
    updateThemeColor();
}

function init() {
    // Initialize theme first
    initializeTheme();

    // Mobile-specific optimizations
    setupMobileOptimizations();

    // Extract note ID from URL path (e.g., /01KDECFWYDMS857DZMCR680MCY)
    const pathname = window.location.pathname;
    const pathParts = pathname.split('/');
    let idFromUrl = null;

    // Look for a 26-character alphanumeric string (ULID pattern)
    for (const part of pathParts) {
        if (part && /^[A-Za-z0-9]{26}$/.test(part)) {
            idFromUrl = part;
            break;
        }
    }

    // Only load/create notes if we're on the note editor page (has noteContent element)
    const noteContent = document.getElementById('noteContent');
    if (noteContent) {
        if (idFromUrl) {
            loadNoteById(idFromUrl);
        } else {
            newNote();
        }
    }

    // Set up auto-save on content change
    if (noteContent) {
        noteContent.addEventListener('input', handleContentChange);
    } else {
        console.error('Element with ID "noteContent" not found');
    }

    // Handle Enter key in ID input
    const noteIdInput = document.getElementById('noteIdInput');
    if (noteIdInput) {
        noteIdInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                loadNoteFromInput();
            }
        });
    } else {
        console.error('Element with ID "noteIdInput" not found');
    }
}

// Mobile-specific optimizations
function setupMobileOptimizations() {
    // Prevent iOS zoom on input focus by ensuring font-size is at least 16px
    const isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);

    if (isMobile) {
        // Handle virtual keyboard on mobile
        const noteContent = document.getElementById('noteContent');
        if (noteContent) {
            noteContent.addEventListener('focus', function() {
                // Small delay to let the virtual keyboard appear
                setTimeout(() => {
                    this.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }, 300);
            });

            // Prevent losing focus when scrolling on mobile
            noteContent.addEventListener('touchmove', function(e) {
                e.stopPropagation();
            }, { passive: true });
        }

        // Update theme color based on current theme
        updateThemeColor();
    }

    // Handle orientation change
    window.addEventListener('orientationchange', function() {
        // Fix viewport height issues on mobile browsers
        setTimeout(() => {
            const vh = window.innerHeight * 0.01;
            document.documentElement.style.setProperty('--vh', `${vh}px`);
        }, 100);
    });

    // Set initial viewport height
    const vh = window.innerHeight * 0.01;
    document.documentElement.style.setProperty('--vh', `${vh}px`);
}

// Update theme color meta tag based on current theme
function updateThemeColor() {
    const themeColorMeta = document.querySelector('meta[name="theme-color"]');
    const currentTheme = document.body.getAttribute('data-theme');

    if (themeColorMeta) {
        if (currentTheme === 'dark') {
            themeColorMeta.setAttribute('content', '#2d2d2d');
        } else {
            themeColorMeta.setAttribute('content', '#007bff');
        }
    }
}

// Handle content change for auto-save
function handleContentChange() {
    const noteContent = document.getElementById('noteContent');
    if (!noteContent) {
        console.error('Element with ID "noteContent" not found in handleContentChange');
        return;
    }
    const content = noteContent.value;

    // Clear existing timeout
    if (autoSaveTimeout) {
        clearTimeout(autoSaveTimeout);
    }

    autoSaveTimeout = setTimeout(() => {
        autoSave(content);
    }, 1000);
}

async function autoSave(content) {
    if (content === lastSavedContent) {
        return;
    }

    try {
        if (currentNoteId) {
            // Update existing note
            await fetch(API_BASE, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    id: currentNoteId,
                    content: content
                })
            });
        } else {
            // Create new note
            const response = await fetch(API_BASE, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    note: content
                })
            });

            if (response.ok) {
                const note = await response.json();
                currentNoteId = note.id;

                // Update URL with new note ID (path-based)
                const newUrl = `/${note.id}`;
                window.history.replaceState({}, '', newUrl);

                // Show note ID in header
                showNoteId(note.id);
            }
        }

        lastSavedContent = content;
    } catch (error) {
        console.error('Auto-save failed:', error);
    }
}

async function loadNoteById(id) {
    try {
        const response = await fetch(`${API_BASE}/${id}`);

        if (response.ok) {
            const note = await response.json();
            currentNoteId = note.id;

            // Update content
            const noteContent = document.getElementById('noteContent');
            if (noteContent) {
                noteContent.value = note.content;
                lastSavedContent = note.content;
            } else {
                console.error('Element with ID "noteContent" not found in loadNoteById');
            }

            // Update URL (path-based)
            const newUrl = `/${note.id}`;
            window.history.replaceState({}, '', newUrl);

            // Show note ID in header
            showNoteId(note.id);
        } else {
            // Note not found (404) or other error - create a new note instead
            console.warn(`Note with ID ${id} not found (${response.status}), creating new note`);
            await newNote();
        }
    } catch (error) {
        // Network error or other failure - create a new note instead
        console.error('Failed to load note:', error, 'creating new note instead');
        await newNote();
    }
}

function showNoteId(id) {
    const noteIdDisplay = document.getElementById('noteIdDisplay');
    if (noteIdDisplay) {
        noteIdDisplay.textContent = id;
        noteIdDisplay.style.display = 'inline-block';
    } else {
        console.error('Element with ID "noteIdDisplay" not found in showNoteId');
    }
}

function hideNoteId() {
    const noteIdDisplay = document.getElementById('noteIdDisplay');
    if (noteIdDisplay) {
        noteIdDisplay.style.display = 'none';
    } else {
        console.error('Element with ID "noteIdDisplay" not found in hideNoteId');
    }
}

async function copyNoteLink() {
    try {
        const noteIdDisplay = document.getElementById('noteIdDisplay');
        if (!noteIdDisplay) {
            console.error('Element with ID "noteIdDisplay" not found');
            return;
        }

        // Get the current URL
        const currentUrl = window.location.href;

        // Copy to clipboard
        await navigator.clipboard.writeText(currentUrl);

        // Visual feedback - temporarily show "Copied!" text
        const originalText = noteIdDisplay.textContent;
        noteIdDisplay.textContent = 'Copied!';
        noteIdDisplay.style.color = 'var(--accent-primary)';

        // Restore original text after 2 seconds
        setTimeout(() => {
            noteIdDisplay.textContent = originalText;
            noteIdDisplay.style.color = '';
        }, 2000);

    } catch (error) {
        console.error('Failed to copy note link:', error);

        // Fallback for older browsers - try to select the URL
        const noteIdDisplay = document.getElementById('noteIdDisplay');
        if (noteIdDisplay) {
            const originalText = noteIdDisplay.textContent;
            noteIdDisplay.textContent = 'Copy failed';
            setTimeout(() => {
                noteIdDisplay.textContent = originalText;
            }, 2000);
        }
    }
}

async function newNote() {
    try {
        const response = await fetch(API_BASE, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                note: ''
            })
        });

        if (response.ok) {
            const note = await response.json();
            currentNoteId = note.id;
            lastSavedContent = '';

            // Clear content and focus on content area
            const noteContent = document.getElementById('noteContent');
            if (noteContent) {
                noteContent.value = '';
                noteContent.focus();
            } else {
                console.error('Element with ID "noteContent" not found in newNote');
            }

            // Update URL with new note ID (path-based)
            const newUrl = `/${note.id}`;
            window.history.replaceState({}, '', newUrl);

            // Show note ID in header
            showNoteId(note.id);
        }
    } catch (error) {
        console.error('Failed to create new note:', error);
    }
}

function showIdInput() {
    const idInputOverlay = document.getElementById('idInputOverlay');
    const noteIdInput = document.getElementById('noteIdInput');

    if (idInputOverlay) {
        idInputOverlay.classList.remove('hidden');
    } else {
        console.error('Element with ID "idInputOverlay" not found in showIdInput');
    }

    if (noteIdInput) {
        noteIdInput.focus();
    } else {
        console.error('Element with ID "noteIdInput" not found in showIdInput');
    }
}

function hideIdInput() {
    const idInputOverlay = document.getElementById('idInputOverlay');
    const noteIdInput = document.getElementById('noteIdInput');

    if (idInputOverlay) {
        idInputOverlay.classList.add('hidden');
    } else {
        console.error('Element with ID "idInputOverlay" not found in hideIdInput');
    }

    if (noteIdInput) {
        noteIdInput.value = '';
    } else {
        console.error('Element with ID "noteIdInput" not found in hideIdInput');
    }
}

function loadNoteFromInput() {
    const noteIdInput = document.getElementById('noteIdInput');
    if (!noteIdInput) return;

    const id = noteIdInput.value.trim();
    if (id) {
        hideIdInput();
        window.location.href = `/${id}`;
    }
}

window.addEventListener('load', init);