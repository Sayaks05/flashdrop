/**
 * Inventory: how many units of a drop are left, and reserving/releasing them safely under concurrency.
 * This is where pessimistic locking, optimistic locking and atomic Redis decrements live.
 */
package com.flashdrop.inventory;
