package hexed;

public class EloRatingSystem {

    // Default rating for a new player
    private static final long DEFAULT_RATING = 1500;
    // K-factor determines the maximum change per game
    private static final long K_FACTOR = 32;

    /**
     * Calculates the new rating for a player.
     *
     * @param currentRating  The current rating of the player.
     * @param opponentRating The rating of the opponent.
     * @param score          The score: 1 for a win, 0.5 for a draw, and 0 for a loss.
     * @return The new rating of the player.
     */
    public static long calculateNewRating(long currentRating, long opponentRating, double score) {
        double expectedScore = 1 / (1.0 + Math.pow(10, (opponentRating - currentRating) / 400.0));
        return (long) (currentRating + K_FACTOR * (score - expectedScore));
    }
    public static double getscore_from_hexes(long capturedhexes, long hexestotal, long totalplayercounts){
        double average_hex_per_person = (double) hexestotal /totalplayercounts;
        if (capturedhexes>average_hex_per_person){
            return 1;
        } else if (capturedhexes==1) {
            return 0.5;
        } else {
            return 0;
        }
    }
}