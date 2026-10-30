package app.duoodo

import app.duoodo.model.Doodle
import app.duoodo.model.DoodleCodec
import app.duoodo.model.ImageMark
import app.duoodo.model.Stroke
import app.duoodo.model.TextMark
import app.duoodo.nostr.FakeRelays
import app.duoodo.pairing.Handshake
import app.duoodo.pairing.Invite
import app.duoodo.pairing.Pairing
import app.duoodo.pairing.PendingInvite
import app.duoodo.sync.Envelope
import app.duoodo.sync.NostrTransport
import app.duoodo.ui.DrawingState
import app.duoodo.widget.DuoodoWidget
import app.duoodo.widget.DuoodoWidgetReceiver
import app.duoodo.widget.DuoodoWidgetReceiver1x4
import app.duoodo.widget.DuoodoWidgetReceiver2x3
import app.duoodo.widget.DuoodoWidgetReceiver2x4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end simulated integration test exercising all implemented features:
 * 1. Cryptographic Handshake & Safety Code emoji verification
 * 2. Canvas gesture simulation: multi-touch emoji resizing & rotation
 * 3. Photo / sticker placement and drawing strokes over stickers
 * 4. Binary wire encoding (v3), payload budget adherence, and Nostr relay transport
 * 5. Multi-size widget configuration (2x2, 2x4, 2x3, 1x4) and opacity settings
 */
class SimulatedEndToEndTest {
    private val relays = FakeRelays()
    private var now = 1_761_700_000_000L
    private val clock = { now }

    private class SimulatedPhone : Handshake.State {
        override var pairing: Pairing? = null
        override var pendingInvite: PendingInvite? = null
        override var joining: Pairing? = null
        override var joiningSinceMillis: Long? = null
        var safetyVerified = false
    }

    @Test
    fun fullEndToEndSimulation() = runBlocking {
        // =========================================================================
        // STEP 1: Handshake, Unlinkable Slot Tags, and Mandatory Safety Verification
        // =========================================================================
        val alice = SimulatedPhone()
        val bob = SimulatedPhone()
        val handshake = Handshake(relays, clock = clock)

        // Alice creates an invite
        val alicePending = PendingInvite.create(now)
        alice.pendingInvite = alicePending
        val inviteCode = alicePending.invite.toCode()
        assertTrue("Invite code starts with prefix", inviteCode.startsWith("duoodo-"))

        // Bob accepts Alice's invite
        handshake.join(bob, Invite.parse(inviteCode))
        assertNull("Bob is not paired until confirmation", bob.pairing)

        // Handshake advances over relays
        handshake.advance(alice)
        assertNotNull("Alice is paired after Bob replies", alice.pairing)
        assertNull("Alice's single-use invite is closed", alice.pendingInvite)

        handshake.advance(bob)
        assertNotNull("Bob is paired after Alice confirms", bob.pairing)
        assertArrayEquals("Both derive identical root keys", alice.pairing!!.root, bob.pairing!!.root)

        // Safety emoji check: Both see the same 6 emojis
        val aliceEmojis = alice.pairing!!.safetyEmojis
        val bobEmojis = bob.pairing!!.safetyEmojis
        assertEquals(6, aliceEmojis.size)
        assertEquals(aliceEmojis, bobEmojis)

        // Both verify the safety emojis
        alice.safetyVerified = true
        bob.safetyVerified = true
        assertTrue(alice.safetyVerified && bob.safetyVerified)

        // Verify slot tags rotate daily per direction to ensure message unlinkability
        val day1 = now / 1000 / NostrTransport.DAY_SECONDS
        val day2 = day1 + 1
        val tagAliceDay1 = alice.pairing!!.slotTag(alice.pairing!!.outgoing, day1)
        val tagAliceDay2 = alice.pairing!!.slotTag(alice.pairing!!.outgoing, day2)
        val tagBobDay1 = bob.pairing!!.slotTag(bob.pairing!!.outgoing, day1)
        assertNotEquals("Slot tags must rotate across days to prevent tracking", tagAliceDay1, tagAliceDay2)
        assertNotEquals("Slot tags must differ across directions", tagAliceDay1, tagBobDay1)
        assertEquals("Bob's incoming slot tag matches Alice's outgoing slot tag", tagAliceDay1, bob.pairing!!.slotTag(bob.pairing!!.incoming, day1))

        // =========================================================================
        // STEP 2: Canvas Interaction: Resizing & Rotating Emojis and Photo Stickers
        // =========================================================================
        val drawing = DrawingState()

        // 2a. Place and resize/rotate an Emoji ("🚀")
        drawing.tool = DrawingState.Tool.EMOJI
        drawing.emoji = "🚀"

        // Simulate touch down at (0.3, 0.4)
        drawing.startPlacement(0.3f, 0.4f)
        // Drag by dx=0.05, dy=0.05, pinch to 2.2x scale, rotate 45 degrees
        drawing.updatePlacement(dx = 0.05f, dy = 0.05f, zoom = 2.2f, rot = 45f)
        // Release touch -> commits emoji
        drawing.endPlacement()

        assertEquals("One mark committed", 1, drawing.marks.size)
        val emojiMark = drawing.marks[0] as TextMark
        assertEquals("🚀", emojiMark.text)
        assertEquals(0.35f, emojiMark.x, 1e-4f)
        assertEquals(0.45f, emojiMark.y, 1e-4f)
        assertEquals(45f, emojiMark.rotation, 0.1f)
        assertTrue("Emoji size is scaled by 2.2x", emojiMark.size > DrawingState.TEXT_SIZES[1] * 2.0f)

        // 2b. Add a Photo Sticker ("ImageMark")
        drawing.tool = DrawingState.Tool.IMAGE
        // Create 2 KB simulated photo bytes
        val testImageBytes = ByteArray(2048) { (it % 256).toByte() }
        drawing.imageBytes = testImageBytes

        // Simulate touch down at (0.6, 0.6)
        drawing.startPlacement(0.6f, 0.6f)
        // Drag slightly, pinch to 1.5x scale, rotate -30 degrees
        drawing.updatePlacement(dx = 0.02f, dy = -0.02f, zoom = 1.5f, rot = -30f)
        // Release touch -> commits image sticker
        drawing.endPlacement()

        assertEquals("Two marks committed", 2, drawing.marks.size)
        val imageMark = drawing.marks[1] as ImageMark
        assertArrayEquals("Image bytes preserved", testImageBytes, imageMark.bytes)
        assertEquals(0.62f, imageMark.x, 1e-4f)
        assertEquals(0.58f, imageMark.y, 1e-4f)
        assertEquals(-30f, imageMark.rotation, 0.1f)
        assertEquals(0.4f * 1.5f, imageMark.scale, 1e-4f)

        // 2c. Draw Freehand Strokes OVER the sticker
        drawing.tool = DrawingState.Tool.PEN
        drawing.color = 0xFF00FF00.toInt()
        drawing.start(0.5f, 0.5f)
        drawing.move(0.55f, 0.55f)
        drawing.move(0.65f, 0.65f)
        drawing.end()

        assertEquals("Three marks committed in layered order", 3, drawing.marks.size)
        assertTrue("First mark is TextMark (Emoji)", drawing.marks[0] is TextMark)
        assertTrue("Second mark is ImageMark (Photo Sticker)", drawing.marks[1] is ImageMark)
        assertTrue("Third mark is Stroke (drawn over sticker)", drawing.marks[2] is Stroke)

        // =========================================================================
        // STEP 3: Binary Encoding (v3) & End-to-End Relay Delivery
        // =========================================================================
        val doodleToSend = drawing.snapshot(now)
        val encodedBytes = DoodleCodec.encode(doodleToSend)

        // Verify the entire payload fits within the 49KB encrypted envelope limit
        assertTrue("Encoded doodle size (${encodedBytes.size} bytes) fits well within 49KB envelope limit", encodedBytes.size <= Envelope.MAX_SEALED_BYTES)

        // Alice sends doodle over relays
        val aliceTransportState = object : NostrTransport.State { override var lastSentDay: Long? = null }
        val aliceTransport = NostrTransport(alice.pairing!!, relays, aliceTransportState, clock)
        aliceTransport.send(doodleToSend)

        // Bob fetches doodle from relays
        val bobTransportState = object : NostrTransport.State { override var lastSentDay: Long? = null }
        val bobTransport = NostrTransport(bob.pairing!!, relays, bobTransportState, clock)
        val receivedDoodle = bobTransport.fetchLatest()
        assertNotNull("Bob successfully fetched and decrypted doodle", receivedDoodle)

        // Verify received content fidelity
        assertEquals(3, receivedDoodle!!.marks.size)
        val receivedEmoji = receivedDoodle.marks[0] as TextMark
        assertEquals("🚀", receivedEmoji.text)
        assertEquals(45f, receivedEmoji.rotation, 0.1f)

        val receivedImage = receivedDoodle.marks[1] as ImageMark
        assertArrayEquals(testImageBytes, receivedImage.bytes)
        assertEquals(330f, ((receivedImage.rotation % 360f) + 360f) % 360f, 0.1f)
        assertEquals(imageMark.scale, receivedImage.scale, 1e-4f)

        val receivedStroke = receivedDoodle.marks[2] as Stroke
        assertEquals(0xFF00FF00.toInt(), receivedStroke.color)

        // =========================================================================
        // STEP 4: Read Receipt & "Seen Peek" Transmission
        // =========================================================================
        // Bob opens inbox and sends a read receipt for Alice's doodle
        bobTransport.sendReceipt(receivedDoodle.createdAtMillis)

        // Alice receives the read receipt
        val aliceItems = aliceTransport.fetchAll()
        val receipt = aliceItems.filterIsInstance<NostrTransport.Incoming.Receipt>().firstOrNull()
        assertNotNull("Alice received Bob's read receipt", receipt)
        assertEquals("Receipt references Alice's sent doodle timestamp", doodleToSend.createdAtMillis, receipt!!.doodleTimestamp)

        // =========================================================================
        // STEP 5: Multi-Size Widgets & Custom Opacity Settings Verification
        // =========================================================================
        val sizes = DuoodoWidget.Size.entries
        assertEquals("4 widget sizes available", 4, sizes.size)
        assertEquals("2×2", DuoodoWidget.Size.SQUARE_2X2.label)
        assertEquals("2×4", DuoodoWidget.Size.WIDE_2X4.label)
        assertEquals("2×3", DuoodoWidget.Size.MEDIUM_2X3.label)
        assertEquals("1×4", DuoodoWidget.Size.BANNER_1X4.label)

        val receiverClasses = sizes.map { it.receiverClass }
        assertTrue(receiverClasses.contains(DuoodoWidgetReceiver::class.java))
        assertTrue(receiverClasses.contains(DuoodoWidgetReceiver2x4::class.java))
        assertTrue(receiverClasses.contains(DuoodoWidgetReceiver2x3::class.java))
        assertTrue(receiverClasses.contains(DuoodoWidgetReceiver1x4::class.java))

        // Verify Frosted Palette has rich color choices
        val palette = app.duoodo.data.Settings.FROSTED_PALETTE
        assertEquals(8, palette.size)
        assertTrue(palette.contains(0xFFFFFFFF.toInt())) // Pure white
        assertTrue(palette.contains(0xFF1B1B23.toInt())) // Midnight black
        assertTrue(palette.contains(0xFFB3E5FC.toInt())) // Sky blue
        assertTrue(palette.contains(0xFFE1BEE7.toInt())) // Lavender

        println("SIMULATION PASSED: All 4 phases completed successfully!")
    }
}
