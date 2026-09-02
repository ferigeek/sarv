# Sarv Frontend Design Specification

## 1. Purpose

This document defines the visual design, layout, interaction behavior, and UI structure of the Sarv frontend.

The frontend implementation agent must follow this document when creating the UI.

This document describes **design requirements**, not implementation details. The agent may choose appropriate technical implementation methods, but must not introduce visual or UX design decisions that contradict or substantially extend the requirements below.

When a functionality is not implemented by the backend or frontend yet, its UI should still be created according to this specification. Such UI should perform no action until the corresponding functionality exists.

---

# 2. Overall Design Direction

## 2.1 Visual Identity

The application is called **Sarv** (Cypress).

The visual identity should combine:

* The color and visual identity associated with a cypress tree, primarily through green.
* Computer and technology aesthetics.
* Binary / Matrix-inspired visual elements.
* Linux-inspired visual character.
* Hacker / terminal-inspired aesthetics.
* A social-platform UI structure similar to established social platforms.

The result should feel like a **technological social network**, rather than a generic modern SaaS application.

## 2.2 Shape Language

The entire application should use **sharp, square geometry**.

Do not use modern rounded-card styling.

UI elements should generally have:

* Square corners.
* Sharp edges.
* Rectangular panels.
* Clearly defined boundaries.

Avoid:

* Large border radii.
* Pill-shaped buttons.
* Rounded cards.
* Excessively soft or floating UI elements.

The interface should visually feel more like a computer system / Linux application than a contemporary rounded web application.

## 2.3 Animation

The application should contain **many cool animations**.

Animations should be an important part of the visual identity rather than being used only occasionally.

Animations may be used for:

* Page loading.
* Application branding.
* Opening and closing windows.
* Search.
* Post interactions.
* Likes and dislikes.
* Navigation.
* Panels appearing/disappearing.
* State changes.
* Other appropriate UI interactions.

Animations should remain usable and should not prevent normal interaction with the application.

---

# 3. Main Application Layout

The authenticated main application is divided into three primary areas:

```text
+----------------+------------------------------------+----------------+
|                |                                    |                |
|     LEFT       |               CENTER               |      RIGHT     |
|                |                                    |                |
| Search         |            Post Feed               |   Sarv Name    |
|                |                                    |                |
| User Summary   |            Posts                  | Hot Topics     |
|                |                                    |                |
| Create Post    |            Posts                  | Platform News  |
|                |                                    |                |
| Navigation     |            Posts                  |                |
|                |                                    |                |
+----------------+------------------------------------+----------------+
```

The **center column is the largest portion of the screen width**.

The center contains the primary scrolling post feed.

The left and right sides contain supporting functionality and information.

---

# 4. Left Sidebar

## 4.1 Search

The search interface is located at the **top of the left sidebar**.

The search bar should allow the user to perform searches without navigating away from the main page.

When activated, search results should appear in a **small window/panel on the same page**.

The search interface must contain tabs for:

1. General search.
2. Search by username and display name.
3. Search by post content.

The search window should be visually consistent with the application's square, technological design.

Search results should appear inside the same page rather than navigating to a completely separate page.

The exact search result data should follow the application's existing documentation/API definitions.

---

## 4.2 User Summary

Below the search interface, display a compact summary of the currently authenticated user.

The summary contains:

* Profile picture, when one exists.
* Display name.
* Username.

The summary should provide access to the user's profile.

---

## 4.3 Create Post

Below the user summary, provide an item/action for creating a post.

Selecting this action should open a post creation window on the current page.

The application should not navigate away from the current page merely to create a post.

---

## 4.4 User Navigation

Below the create-post action, provide navigation items such as:

* View profile.
* History of liked posts.
* Following users.
* Followers / followed users.

The exact available information and API data should follow the project documentation.

Items whose backend functionality does not yet exist should still be displayed in the UI, but clicking them should perform no action.

---

# 5. Profile

## 5.1 Profile View

Selecting the user's profile should display the user's profile information.

The profile view should contain the profile information defined by the project's documentation.

The profile should also provide the ability to modify information that the authenticated user is allowed to modify.

Fields that are not allowed to be modified must not be presented as editable.

The UI should distinguish between:

* Information that can be viewed.
* Information that can be modified.

---

# 6. Followers and Following

When the user opens the followers/following section, display a list of users.

Each user summary should contain:

* Profile picture, when available.
* Username.
* Display name.

The exact data represented in these user summaries should follow the project's documentation.

The UI should use a consistent user-summary component for these lists.

---

# 7. Main Post Feed

## 7.1 Position

The post feed occupies the **center and largest section of the main layout**.

Posts should be displayed vertically.

The feed should be scrollable.

The surrounding left and right areas should remain separate from the main feed.

## 7.2 Post Appearance

Each post should clearly display its content and interaction controls.

Posts must display:

* Post content.
* View count.
* Like count.
* Dislike count.

Other post information should follow the project's existing API/documentation definitions.

## 7.3 Like and Dislike

Likes and dislikes must use:

* **Thumbs-up icon** for like.
* **Thumbs-down icon** for dislike.

Do **not** use a heart icon for likes.

When a post is liked by the viewer, the thumbs-up icon should be colored **green**.

When a post is disliked by the viewer, the thumbs-down icon should be colored **red**.

After a successful like interaction, display a **pixelated smiling emoji** as feedback.

After a successful dislike interaction, display a **pixelated sad emoji** as feedback.

The feedback animation should be noticeable and visually fit the technological/pixelated identity.

## 7.4 Post Actions

Posts should provide interaction options comparable to a typical social-media platform.

The post actions include:

* Like.
* Dislike.
* Repost.
* Quote.
* Comment.

The UI should provide these actions even when some corresponding functionality has not yet been implemented.

If functionality is not implemented, clicking the UI element should currently do nothing.

---

# 8. Post Creation

## 8.1 Opening

Selecting "Create Post" should open a **window/panel on the current page**.

The user should remain on the current page while creating the post.

## 8.2 Content

The creation interface should contain a section for entering post content.

## 8.3 Media

The creation interface should contain a section for attaching media.

The media workflow must be handled correctly:

1. The user selects/attaches media.
2. The media is uploaded.
3. The media upload completes successfully.
4. Only after successful media upload should the actual post be submitted.

The frontend must therefore treat media upload as a separate step that must successfully complete before post submission.

## 8.4 Unimplemented Functionality

Even if some post-creation functionality has not yet been implemented, the corresponding UI should still exist.

Unavailable backend functionality must not prevent the rest of the interface from being implemented.

---

# 9. Right Sidebar

The right sidebar contains three primary sections.

## 9.1 Sarv Application Name

At the top of the right sidebar, display the application name:

**Sarv**

The application name should have a:

* Matrix-like appearance.
* Green color identity.
* Computer/terminal-inspired typography.
* Technological/hacker visual character.

### Page-load animation

The word **Sarv should be constructed through animation when the page loads**.

The application name should not simply appear instantly.

The animation should make it feel as though the name is being generated/constructed by a computer system.

The precise implementation of this animation is left to the implementation agent, but its visual result must match the Matrix/computer/hacker identity described above.

---

## 9.2 Hottest Topics

Below the application name, display a section containing the **hottest topics**.

The presentation should resemble the topic/trending section commonly found on social platforms such as Twitter/X.

Topics should be presented as a list.

The section should visually fit the Sarv technological theme.

The exact topic data should come from the application's available data/API definitions.

---

## 9.3 Platform Releases / News

Below the hottest topics section, display another section containing information about the platform itself.

This section may contain:

* New releases.
* New features.
* Platform news.

The section should be visually distinct from the hottest-topics section while remaining part of the same right sidebar.

---

# 10. Authentication

The application uses **JWT authentication**.

The frontend must determine whether the user is authenticated.

## 10.1 Unauthenticated State

If the user is not logged in, the application should show the login interface instead of the authenticated social-platform interface.

The login page should be simple.

The primary login UI should be a **box centered on the page**.

It should provide:

* Login functionality.
* Registration option.
* Login fields.

The authentication UI should retain the application's overall square and technological design language.

---

# 11. Login

The login interface should contain the fields required for authentication.

It should provide an obvious way to switch to registration.

The layout should remain simple rather than becoming a large or complicated authentication page.

---

# 12. Registration

Registration is a two-step process.

## 12.1 Step 1 — Mandatory Information

The first registration step asks the user for the mandatory registration fields.

Only required information should be requested at this stage.

## 12.2 Step 2 — Optional Information

After the mandatory registration step, the application should show a second step for additional arbitrary information fields.

These fields are optional.

The user must have a clear ability to **skip this step**.

The user should therefore be able to successfully complete registration without providing optional information.

---

# 13. Interaction and UI Behavior

The UI should generally behave like a social-platform interface while maintaining the Sarv visual identity.

Important interaction principles:

* Keep the user on the current page when opening contextual windows/panels.
* Use animated transitions when opening and closing UI windows where appropriate.
* Provide visual feedback for important interactions.
* Maintain consistent square geometry throughout the interface.
* Use pixel/computer-inspired visual feedback where appropriate.
* Do not replace unavailable functionality with invented behavior.

When a feature exists only in the UI but has no implementation yet:

```text
UI exists
    ↓
User clicks it
    ↓
No action
```

Do not invent mock backend behavior unless explicitly requested elsewhere.

---

# 14. Responsive / Layout Priority

The primary desktop layout consists of:

```text
Left sidebar | Large center feed | Right sidebar
```

The center feed is the largest region.

The implementation should preserve this hierarchy.

The exact breakpoint behavior, widths, and responsive strategy are implementation details and are not specified by this document. The implementation must not change the fundamental importance of the center feed.

---

# 15. Visual Language Summary

The entire application should consistently communicate the following visual concepts:

```text
Sarv
│
├── Cypress / Green
├── Matrix
├── Binary
├── Computer
├── Linux
├── Hacker
├── Pixel / Retro Computer
├── Social Platform
├── Sharp / Square Geometry
└── Heavy Animation
```

The application should feel like a **social network designed as a computer-oriented interface**.

It should not look like a generic modern rounded web application.

---

# 16. Explicit Design Constraints

The implementation agent must preserve these requirements:

### Geometry

* Sharp/square UI.
* No modern large rounded corners.
* No pill-shaped visual language.
* Rectangular UI elements.

### Color / Identity

* Green must be an important part of the theme.
* The visual identity should communicate cypress, Matrix, computers, Linux, and hacker aesthetics.

### Animation

* Animations should be used extensively.
* The Sarv name must be constructed through animation when the page loads.
* Interaction feedback should include animations.

### Main Layout

* Left sidebar.
* Large center feed.
* Right sidebar.
* Center feed is the largest section.

### Left Sidebar

* Search at the top.
* Search opens results on the same page.
* Search tabs:

  * General.
  * Username/display name.
  * Post content.
* User summary.
* Create-post action.
* Profile/navigation items.

### Center

* Scrollable social feed.
* Posts.
* View count.
* Like count.
* Dislike count.
* Thumbs-up like.
* Thumbs-down dislike.
* Pixelated happy feedback after liking.
* Pixelated sad feedback after disliking.
* Repost.
* Quote.
* Comment.

### Right Sidebar

* Animated Matrix/computer-style Sarv name.
* Hottest topics.
* Platform releases/features/news.

### Profile

* View profile.
* Edit information that is allowed to be modified.

### Followers / Following

* User profile picture.
* Username.
* Display name.

### Post Creation

* Opens on current page.
* Content input.
* Media attachment.
* Media must be uploaded successfully before post submission.

### Authentication

* JWT authentication.
* Unauthenticated users see login.
* Login centered in a simple box.
* Registration available.
* Registration has:

  * Mandatory-information step.
  * Optional-information step.
  * Ability to skip optional information.

### Missing Features

* Build the UI even when functionality does not yet exist.
* Unimplemented controls should do nothing when clicked.
* Do not invent backend behavior.

---

# 17. Relationship With Project Documentation

The existing project documentation is the authoritative source for:

* API data.
* User fields.
* Post fields.
* Search results.
* Profile information.
* Follower/following information.
* Authentication requirements.
* Other domain-specific data.

This design document defines **how the information should be presented**, while the project documentation defines **what information exists**.

The implementation agent should therefore consult the project documentation rather than inventing fields or data structures.

---

# 18. Implementation Freedom

This document intentionally does **not** define implementation-specific decisions such as:

* Specific frontend libraries.
* Component names.
* Exact pixel dimensions.
* Exact font files.
* Exact color hex values.
* Exact animation library.
* Exact API implementation.
* Exact responsive breakpoints.
* Exact spacing values.

Those are implementation concerns unless specified elsewhere in the project.

The implementation agent should choose technically appropriate solutions while preserving the visual and behavioral requirements in this document.

Any unspecified visual decision should remain conservative and consistent with the documented Sarv design rather than introducing an unrelated design style.
