/* Backend API types — field-for-field match with the Spring Boot contracts
   (see docs/docs/en/5-Backend.md and the controller/DTO sources). */

export type Gender = 'MALE' | 'FEMALE' | 'RATHER_NOT_TO_SAY'

export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'BANNED' | 'DELETED'

export type PostCategory = 'NORMAL' | 'COMMENT' | 'REPOST' | 'QUOTE'

export type ReactionType = 1 | -1

export type UserReaction = -1 | 0 | 1

export interface UserResponse {
  id: number
  username: string
  displayName: string
  bio: string | null
  gender: Gender
  location: string | null
  profilePictureId: number | null
  status: UserStatus
}

export interface UserSummaryResponse {
  id: number
  username: string
  displayName: string
  profilePictureId: number | null
}

export interface UserRegisterResponse {
  id: number
  username: string
  displayName: string
  email: string
  token: string
}

export interface PostResponse {
  id: number
  userId: number
  postCategory: PostCategory
  content: string | null
  createdAt: string
  updatedAt: string | null
  mediaId: number | null
  repostOfId: number | null
  parentId: number | null
  viewCount: number
  likeCount: number
  dislikeCount: number
}

export interface ReactionResponse {
  likeCount: number
  dislikeCount: number
  userReaction: UserReaction
}

export interface MediaResponse {
  id: number
  url: string
}

export interface MediaMetadataResponse {
  id: number
  size: number
  name: string
  mimeType: string
  createdAt: string
}

export interface Page<T> {
  content: T[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

export interface Pageable {
  page?: number
  size?: number
}

/* RFC 9457 ProblemDetail returned by the backend for handled errors. */
export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail: string
  instance: string
}