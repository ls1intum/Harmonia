import { useParams, useNavigate, useLocation } from "react-router-dom";
import { useState, useEffect } from "react";
import TeamDetail from "@/components/TeamDetail";
import type { Team } from "@/types/team";
import { loadTeamById } from "@/data/dataLoaders";
import { toast } from "@/hooks/use-toast";

export default function TeamDetailPage() {
    const { teamId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    const { course, exercise } = location.state || {};

    const [team, setTeam] = useState<Team | null>(location.state?.team || null);
    const [isLoading, setIsLoading] = useState(!location.state?.team);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        // If we already have team data from navigation state, no need to fetch
        if (team) {
            return;
        }

        // Otherwise, fetch team data by ID
        if (!teamId) {
            navigate('/');
            return;
        }

        const fetchTeam = async () => {
            setIsLoading(true);
            setError(null);

            try {
                const fetchedTeam = await loadTeamById(teamId);

                if (!fetchedTeam) {
                    toast.error('Team not found');
                    navigate('/teams');
                    return;
                }

                setTeam(fetchedTeam);
            } catch (err) {
                console.error('Error fetching team:', err);
                const errorMessage = err instanceof Error ? err.message : 'Failed to load team';
                setError(errorMessage);
                toast.error(errorMessage);
            } finally {
                setIsLoading(false);
            }
        };

        fetchTeam();
    }, [teamId, team, navigate]);

    if (isLoading) {
        return (
            <div className="min-h-screen bg-background flex items-center justify-center">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary mx-auto mb-4"></div>
                    <p className="text-muted-foreground">Loading team details...</p>
                </div>
            </div>
        );
    }

    if (error || !team) {
        return (
            <div className="min-h-screen bg-background flex items-center justify-center">
                <div className="text-center">
                    <p className="text-destructive mb-4">{error || 'Team not found'}</p>
                    <button
                        onClick={() => navigate('/teams')}
                        className="text-primary hover:underline"
                    >
                        Back to Teams
                    </button>
                </div>
            </div>
        );
    }

    return (
        <TeamDetail
            team={team}
            onBack={() => navigate('/teams', { state: { course, exercise } })}
            course={course}
            exercise={exercise}
        />
    );
}