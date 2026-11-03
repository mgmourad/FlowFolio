package com.flowfolio.core.model

/**
 * Why a book is sitting where it is (owner, M5): the reader's own intention for it, rather than a reading state the
 * app worked out.
 *
 * "In progress", "Finished" and the rest are facts about pages turned. These are not -- a book can be untouched for
 * a year and still be [PAUSED] rather than abandoned -- so they are set by hand and never inferred.
 */
enum class ReadingIntent { NEEDS_MOOD, TO_STUDY, PAUSED, WITH_SOMEONE }
