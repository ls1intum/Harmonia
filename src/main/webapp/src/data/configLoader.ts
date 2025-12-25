import axios from 'axios';

export interface ProjectProfile {
  id: string;
  courseName: string;
  semester: string;
  exerciseId: number;
  gitRepoPath: string;
}

export async function fetchProjectProfiles(): Promise<ProjectProfile[]> {
  try {
    const response = await axios.get('/api/config/projects');
    return response.data;
  } catch (error) {
    console.error('Failed to fetch project profiles:', error);
    return [];
  }
}
