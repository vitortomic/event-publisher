const express = require('express');
const app = express();
const port = 8081;

// Middleware to parse JSON
app.use(express.json());

// Store current scores for each event
const scores = new Map();

// Interval to randomly update scores
const scoreUpdateInterval = 10000; // 10 seconds

// Start the score update interval
setInterval(() => {
  updateRandomScores();
}, scoreUpdateInterval);

// Function to randomly update scores for active matches
function updateRandomScores() {
  for (const [eventId, score] of scores.entries()) {
    // Randomly decide which team scores (0 = left side, 1 = right side, or no score)
    const random = Math.random();
    
    if (random < 0.45) {
      // Left team scores
      const currentScores = score.currentScore.split(':');
      const leftScore = parseInt(currentScores[0]);
      currentScores[0] = (leftScore + 1).toString();
      score.currentScore = currentScores.join(':');
      console.log(`Event ${eventId}: Left team scored! New score: ${score.currentScore}`);
    } else if (random < 0.9) {
      // Right team scores
      const currentScores = score.currentScore.split(':');
      const rightScore = parseInt(currentScores[1]);
      currentScores[1] = (rightScore + 1).toString();
      score.currentScore = currentScores.join(':');
      console.log(`Event ${eventId}: Right team scored! New score: ${score.currentScore}`);
    } else {
      // No team scores this time
      console.log(`Event ${eventId}: No goals this round`);
    }
  }
}

// Endpoint to get current score for an event
app.get('/:eventId/score', (req, res) => {
  const eventId = req.params.eventId;
  
  // If this event doesn't exist, initialize it with 0:0
  if (!scores.has(eventId)) {
    scores.set(eventId, {
      eventId: eventId,
      currentScore: '0:0'
    });
    console.log(`New event created: ${eventId}`);
  }
  
  const score = scores.get(eventId);
  
  res.json({
    eventId: score.eventId,
    currentScore: score.currentScore
  });
});

// Endpoint to get all active scores (for debugging)
app.get('/debug/all', (req, res) => {
  const allScores = Array.from(scores.entries()).map(([eventId, score]) => score);
  res.json(allScores);
});

app.listen(port, () => {
  console.log(`Soccer server listening at http://localhost:${port}`);
  console.log(`Example: GET /event-123/score`);
});